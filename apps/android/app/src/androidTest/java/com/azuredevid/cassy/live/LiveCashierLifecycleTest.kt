package com.azuredevid.cassy.live

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.azuredevid.cassy.data.repository.ServerSettingsRepository
import com.azuredevid.cassy.domain.model.CashMovementType
import com.azuredevid.cassy.domain.repository.AuthRepository
import com.azuredevid.cassy.domain.repository.PrinterSettingsRepository
import com.azuredevid.cassy.domain.repository.ShiftRepository
import com.azuredevid.cassy.ui.pos.PosViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * S2 cashier lifecycle against the Docker test stack (Docs/PRD/PrdOpsAndPwaClose.md section 10).
 *
 * Drives the real [PosViewModel] and Ktor repositories from Koin, so the payment coordinator,
 * the print dispatcher and the server printer config are all exercised; only the UI is skipped.
 * Store `e2e-kasir` comes from cassy-backend db/seeds/E2eLifecycle.sql. Receipts are read back as
 * text from the virtual ESC/POS printer (job API on 9101). Run by `android-live-e2e.yml` only.
 */
@RunWith(AndroidJUnit4::class)
class LiveCashierLifecycleTest {

    private val koin get() = GlobalContext.get()

    @Before
    fun loginAsE2eCashier(): Unit = runBlocking {
        koin.get<ServerSettingsRepository>().setBaseUrl("http://$HOST:18080")
        koin.get<AuthRepository>()
            .login(storeSlug = STORE, email = "cashier.e2e@cassy.local", pin = CASHIER_PIN)
            .getOrThrow()
        http("DELETE", "$HOST_URL:9101/jobs")
    }

    @Test
    fun cashAndEdcSettleWithServerTotalsAndPrintFromServerPrinterConfig(): Unit = runBlocking {
        val shifts = koin.get<ShiftRepository>()
        val drawer = shifts.getDrawers().getOrThrow().first { it.name == "Laci Uji 1" }
        // OPS-01: open the shift and record a cash-in.
        val shift = shifts.openShift(openingFloat = OPENING_FLOAT, drawerId = drawer.id).getOrThrow().shift
        shifts.recordCashMovement(CashMovementType.CASH_IN, 50_000L, "Uang kecil", shift.id, drawer.id).getOrThrow()

        val vm = onMain { koin.get<PosViewModel>() }
        awaitTrue("catalog") { vm.uiState.value.allProducts.any { it.name == "Kopi Uji" } }
        val printers = koin.get<PrinterSettingsRepository>()
        awaitTrue("server printer config") { printers.getConfig().hasCashierPrinter }

        // OPS-02: cash sale, totals from the server quote only.
        val cashTotal = sell(vm, "Kopi Uji")
        onMain { vm.submitCashPayment(tenderedAmount = 50_000L, drawerId = drawer.id) }
        awaitTrue("cash settled") { vm.uiState.value.cart.isEmpty() && !vm.uiState.value.isSubmittingOrder }
        assertNotNull(vm.uiState.value.lastSettledOrderId)

        // OPS-12: card on the store's EDC terminal, recorded with the slip reference.
        sell(vm, "Nasi Uji")
        onMain { vm.submitEdcPayment(reference = "APPR-E2E-01", drawerId = drawer.id) }
        awaitTrue("edc settled") { vm.uiState.value.cart.isEmpty() && !vm.uiState.value.isSubmittingOrder }

        // OPS-11: the receipt printer from the server prints 2 copies with its own footer.
        val receipts = awaitJobs { jobs -> jobs.count { "Terima kasih - E2E" in it } >= 4 }
            .filter { "Terima kasih - E2E" in it }
        assertEquals("2 copies for each of the 2 sales", 4, receipts.size)
        assertTrue(receipts.any { "TUNAI" in it && rupiah(cashTotal) in it })
        assertTrue(receipts.any { "EDC" in it && "Nasi Uji" in it })
        assertTrue("an EDC receipt has no change line", receipts.filter { "EDC" in it }.none { "Kembali" in it })

        // Leave the drawer clean: count the float, the cash-in and the cash sale.
        shifts.closeShift(shift.id, countedCash = OPENING_FLOAT + 50_000L + cashTotal).getOrThrow()
    }

    /** Adds one [productName] to the cart and returns the server-quoted total. */
    private suspend fun sell(vm: PosViewModel, productName: String): Long {
        val product = vm.uiState.value.allProducts.first { it.name == productName }
        onMain { vm.addProductToCart(product) }
        awaitTrue("quote for $productName") { vm.uiState.value.quote != null && !vm.uiState.value.isQuoting }
        return vm.uiState.value.quote!!.totalAmount
    }

    private fun rupiah(amount: Long) = "%,d".format(amount).replace(',', '.')

    private suspend fun awaitJobs(done: (List<String>) -> Boolean): List<String> {
        var texts = emptyList<String>()
        awaitTrue("printer jobs") {
            val jobs = JSONArray(http("GET", "$HOST_URL:9101/jobs"))
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

    private fun http(method: String, url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 5_000
        conn.readTimeout = 5_000
        return try {
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private companion object {
        const val HOST = "10.0.2.2"
        const val HOST_URL = "http://$HOST"
        const val STORE = "e2e-kasir"
        const val CASHIER_PIN = "528307"
        const val OPENING_FLOAT = 200_000L
        const val TIMEOUT_MS = 15_000L
    }
}
