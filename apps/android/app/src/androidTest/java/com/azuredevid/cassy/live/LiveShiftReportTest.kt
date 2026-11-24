package com.azuredevid.cassy.live

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.azuredevid.cassy.domain.model.CashDrawer
import com.azuredevid.cassy.domain.model.CreatePosOrderInput
import com.azuredevid.cassy.domain.model.CreatePosOrderItemInput
import com.azuredevid.cassy.domain.model.InitiatePosPaymentInput
import com.azuredevid.cassy.domain.model.PosOrder
import com.azuredevid.cassy.domain.repository.CatalogRepository
import com.azuredevid.cassy.domain.repository.PosOrderRepository
import com.azuredevid.cassy.domain.repository.ShiftRepository
import com.azuredevid.cassy.ui.shift.ShiftViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.parameter.parametersOf
import java.util.UUID

/**
 * S3 X and Z reports against the Docker test stack (OPS-08, PrdOpsAndPwaClose.md section 11, S3-Q5).
 * One shift on "Laci Uji 1" with a cash and an EDC sale; the owner's day summary must move by exactly
 * what the shift took. The cashier closes blind; the printed X and Z are read back from the virtual printer.
 */
@RunWith(AndroidJUnit4::class)
class LiveShiftReportTest {

    private val koin get() = GlobalContext.get()

    private lateinit var drawer: CashDrawer

    @Before
    fun startClean(): Unit = runBlocking {
        drawer = LiveStack.startOnDrawer("Laci Uji 1")
        LiveStack.http("DELETE", LiveStack.PRINTER_JOBS)
    }

    @Test
    fun blindCloseAndDaySummaryMoveByExactlyWhatTheShiftTook(): Unit = runBlocking {
        val managerToken = LiveStack.staffToken("manager.e2e@cassy.local", LiveStack.MANAGER_PIN)
        val before = LiveStack.api("GET", "/reports/summary", managerToken)

        val shifts = koin.get<ShiftRepository>()
        val shift = shifts.openShift(openingFloat = OPENING_FLOAT, drawerId = drawer.id).getOrThrow().shift
        val cashOrder = sell("Kopi Uji", "cash")
        val edcOrder = sell("Nasi Uji", "edc")

        // Blind count: the cashier's X-report hides the expected cash, the manager's shows it.
        assertNull("cashier X-report hides expected cash", shifts.getXReport(shift.id).getOrThrow().expectedCash)
        val expected = LiveStack.api("GET", "/pos/shifts/${shift.id}/x-report", managerToken).getLong("expected_cash")

        val vm = onMain { koin.get<ShiftViewModel> { parametersOf(SavedStateHandle()) } }
        onMain { vm.onDrawerSelected(drawer.id) }
        awaitTrue("current shift") { vm.uiState.value.currentShift?.id == shift.id }
        onMain { vm.openXReportModal() }
        awaitTrue("x-report") { vm.uiState.value.currentXReport != null }
        onMain { vm.printCurrentXReport() }
        val xPrint = awaitJobs { jobs -> jobs.any { X_TITLE in it } }.first { X_TITLE in it }
        assertTrue("printed X keeps the count blind", BLIND_LINE in xPrint)

        // The cashier counts exactly what the drawer should hold, so no supervisor PIN is needed.
        onMain {
            vm.openCloseShiftModal()
            vm.onCountedCashChanged(expected.toString())
            vm.submitCloseShift()
        }
        awaitTrue("z-report") { vm.uiState.value.lastZReport != null }
        val z = vm.uiState.value.lastZReport!!
        assertEquals(expected, z.expectedCash)
        assertEquals(0L, z.variance)
        assertEquals(OPENING_FLOAT + z.summary.cashSales, z.expectedCash)
        val zPrint = awaitJobs { jobs -> jobs.any { Z_TITLE in it } }.first { Z_TITLE in it }
        assertTrue("printed Z shows the counted cash", "Fisik Kas Dihitung" in zPrint && rupiah(expected) in zPrint)

        val after = LiveStack.api("GET", "/reports/summary", managerToken)
        assertEquals(
            "day cash sales move by the shift's cash sales",
            z.summary.cashSales, after.getLong("cash_sales") - before.getLong("cash_sales")
        )
        assertEquals(
            "day total moves by both sales",
            cashOrder.totalAmount + edcOrder.totalAmount, after.getLong("total_amount") - before.getLong("total_amount")
        )
        assertEquals(2L, after.getLong("orders") - before.getLong("orders"))
    }

    /** Creates a one-item POS order and settles it with [method] on this drawer. */
    private suspend fun sell(productName: String, method: String): PosOrder {
        val product = koin.get<CatalogRepository>().getStaffCatalog().getOrThrow().products.first { it.name == productName }
        val orders = koin.get<PosOrderRepository>()
        val order = orders.createOrder(
            CreatePosOrderInput(
                idempotencyKey = UUID.randomUUID().toString(),
                customerName = "Uji X/Z",
                orderType = "dine_in",
                items = listOf(CreatePosOrderItemInput(product.id, 1))
            )
        ).getOrThrow()
        orders.initiatePayment(
            InitiatePosPaymentInput(
                orderId = order.id,
                idempotencyKey = UUID.randomUUID().toString(),
                method = method,
                cashTenderedAmount = if (method == "cash") CASH_TENDERED else null,
                drawerId = drawer.id,
                reconciliationRef = if (method == "edc") "APPR-E2E-XZ" else null
            )
        ).getOrThrow()
        return order
    }

    private fun rupiah(amount: Long) = "Rp " + "%,d".format(amount).replace(',', '.')

    private suspend fun awaitJobs(done: (List<String>) -> Boolean): List<String> {
        var texts = emptyList<String>()
        awaitTrue("printer jobs") {
            val jobs = JSONArray(LiveStack.http("GET", LiveStack.PRINTER_JOBS))
            texts = (0 until jobs.length()).map { jobs.getJSONObject(it).getString("text") }
            done(texts)
        }
        return texts
    }

    private suspend fun awaitTrue(what: String, condition: suspend () -> Boolean) {
        try {
            withTimeout(TIMEOUT_MS) { while (!condition()) delay(200) }
        } catch (e: Exception) {
            throw AssertionError("timed out waiting for $what", e)
        }
    }

    private fun <T> onMain(block: () -> T): T {
        var result: T? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync { result = block() }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private companion object {
        const val OPENING_FLOAT = 200_000L
        const val CASH_TENDERED = 50_000L
        const val X_TITLE = "X-REPORT"
        const val Z_TITLE = "Z-REPORT"
        const val BLIND_LINE = "Disamarkan (Blind Count)"
        const val TIMEOUT_MS = 15_000L
    }
}
