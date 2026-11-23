package com.azuredevid.cassy.live

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.azuredevid.cassy.domain.model.CashDrawer
import com.azuredevid.cassy.domain.model.CreatePosOrderItemInput
import com.azuredevid.cassy.domain.model.InitiatePosPaymentInput
import com.azuredevid.cassy.domain.repository.CatalogRepository
import com.azuredevid.cassy.domain.repository.PosOrderRepository
import com.azuredevid.cassy.domain.repository.ShiftRepository
import com.azuredevid.cassy.domain.repository.TabItemVoidInput
import com.azuredevid.cassy.domain.repository.TableTabRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import java.util.UUID

/**
 * S2 table tab lifecycle against the Docker test stack (OPS-03, OPS-04, OPS-05), through the
 * app's own Ktor repositories. Uses drawer "Laci Uji 2" so it never shares a shift with
 * [LiveCashierLifecycleTest]. Store `e2e-kasir` comes from cassy-backend db/seeds/E2eLifecycle.sql.
 */
@RunWith(AndroidJUnit4::class)
class LiveTableTabLifecycleTest {

    private val koin get() = GlobalContext.get()

    private lateinit var drawer: CashDrawer

    @Before
    fun startClean(): Unit = runBlocking {
        drawer = LiveStack.startOnDrawer("Laci Uji 2")
    }

    @Test
    fun tabRoundsVoidAboveThresholdWithManagerPinAndSplitInTwo(): Unit = runBlocking {
        val shifts = koin.get<ShiftRepository>()
        val tabs = koin.get<TableTabRepository>()
        val orders = koin.get<PosOrderRepository>()
        val shift = shifts.openShift(openingFloat = OPENING_FLOAT, drawerId = drawer.id).getOrThrow().shift
        val products = koin.get<CatalogRepository>().getStaffCatalog().getOrThrow().products.associateBy { it.name }

        // OPS-03: open a tab on table U1 and send one round to the kitchen.
        val table = tabs.listTables().getOrThrow().first { it.tableNumber == "U1" }
        val tab = tabs.openTab(UUID.randomUUID().toString(), table.id, table.tableNumber).getOrThrow()
        tabs.submitRound(
            tab.id, UUID.randomUUID().toString(),
            listOf(
                CreatePosOrderItemInput(products.getValue("Tumpeng Uji").id, 1),
                CreatePosOrderItemInput(products.getValue("Kopi Uji").id, 2)
            )
        ).getOrThrow()
        val beforeVoid = tabs.getTab(tab.id).getOrThrow()
        val tumpeng = beforeVoid.items.first { it.snapshotName == "Tumpeng Uji" }

        // OPS-05: Rp 150.000 is above the Rp 100.000 cashier limit. The cashier PIN alone is refused,
        // a store manager's PIN authorises it (K-04).
        val refused = tabs.voidItem(tab.id, tumpeng.id, TabItemVoidInput(1, "Salah input", CASHIER_PIN))
        assertTrue("cashier PIN alone must not void above the threshold", refused.isFailure)
        val afterVoid = tabs.voidItem(
            tab.id, tumpeng.id, TabItemVoidInput(1, "Salah input", CASHIER_PIN, ownerPin = MANAGER_PIN)
        ).getOrThrow()
        assertTrue("void lowers the server total", afterVoid.totalAmount < beforeVoid.totalAmount)

        // OPS-04: close the bill in two equal shares and pay each in cash.
        val closed = tabs.closeTab(tab.id, splitCount = 2).getOrThrow()
        assertEquals(2, closed.splits.size)
        assertEquals("shares add up to the bill to the rupiah", closed.order.totalAmount, closed.splits.sumOf { it.totalAmount })
        var cashIn = 0L
        closed.splits.forEach { share ->
            val paid = orders.initiatePayment(
                InitiatePosPaymentInput(
                    orderId = tab.id,
                    idempotencyKey = UUID.randomUUID().toString(),
                    method = "cash",
                    cashTenderedAmount = share.totalAmount + 1_000L,
                    drawerId = drawer.id,
                    splitId = share.id
                )
            ).getOrThrow()
            cashIn += paid.payment.amount + (paid.payment.roundingAmount ?: 0L)
        }
        assertEquals("paid", orders.getOrder(tab.id).getOrThrow().order.paymentStatus)

        shifts.closeShift(shift.id, countedCash = OPENING_FLOAT + cashIn).getOrThrow()
    }

    private companion object {
        const val CASHIER_PIN = LiveStack.CASHIER_PIN
        const val MANAGER_PIN = LiveStack.MANAGER_PIN
        const val OPENING_FLOAT = 100_000L
    }
}
