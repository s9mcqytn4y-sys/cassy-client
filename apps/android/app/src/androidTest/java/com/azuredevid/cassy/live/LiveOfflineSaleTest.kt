package com.azuredevid.cassy.live

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.azuredevid.cassy.data.local.OfflineOrderDao
import com.azuredevid.cassy.data.local.OfflinePricing
import com.azuredevid.cassy.data.local.OfflineSyncCoordinator
import com.azuredevid.cassy.domain.model.CashDrawer
import com.azuredevid.cassy.domain.repository.AuthRepository
import com.azuredevid.cassy.domain.repository.DeviceProfileRepository
import com.azuredevid.cassy.domain.repository.PrinterSettingsRepository
import com.azuredevid.cassy.domain.repository.ShiftRepository
import com.azuredevid.cassy.ui.pos.PosViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.parameter.parametersOf
import kotlin.random.Random

/**
 * S4 OPS-09 (PrdOpsAndPwaClose.md section 12): a registered tablet loses the server, sells for cash at its
 * own estimate, prints the slip on the LAN, and syncs the order exactly once when the server is back.
 * Uses "Laci Uji 2"; the device is registered through the owner API and revoked afterwards (S4-Q5).
 */
@RunWith(AndroidJUnit4::class)
class LiveOfflineSaleTest {

    private val koin get() = GlobalContext.get()

    private lateinit var drawer: CashDrawer
    private lateinit var managerToken: String
    private lateinit var deviceId: String
    private var shiftId: String? = null

    @Before
    fun pairTabletAndOpenShift(): Unit = runBlocking {
        drawer = LiveStack.startOnDrawer("Laci Uji 2")
        managerToken = LiveStack.staffToken("manager.e2e@cassy.local", LiveStack.MANAGER_PIN)
        val device = LiveStack.api(
            "POST", "/store/devices", managerToken,
            JSONObject().put("name", "Tablet Uji Offline").put("prefix", "O" + Random.nextInt(100, 1000))
        )
        deviceId = device.getString("id")

        // The tablet signs in with its device credential, as after scanning the pairing QR.
        val auth = koin.get<AuthRepository>()
        auth.login(
            storeSlug = "e2e-kasir",
            email = "cashier.e2e@cassy.local",
            pin = LiveStack.CASHIER_PIN,
            deviceCredential = device.getString("credential")
        ).getOrThrow()
        auth.fetchMobileBootstrap().getOrThrow()
        koin.get<DeviceProfileRepository>().profile().getOrThrow()

        shiftId = koin.get<ShiftRepository>().openShift(openingFloat = OPENING_FLOAT, drawerId = drawer.id).getOrThrow().shift.id
        LiveStack.http("DELETE", LiveStack.PRINTER_JOBS)
    }

    @After
    fun cleanUp() {
        LiveStack.setApiReachable(true)
        shiftId?.let {
            runCatching {
                LiveStack.api("POST", "/pos/shifts/$it/force-close", managerToken, JSONObject().put("reason", "Akhir uji offline e2e"))
            }
        }
        runCatching { LiveStack.api("POST", "/store/devices/$deviceId/revoke", managerToken, JSONObject()) }
    }

    @Test
    fun cashSaleWhileCutOffPrintsLocallyAndSyncsExactlyOnce(): Unit = runBlocking {
        val vm = onMain { koin.get<PosViewModel> { parametersOf(SavedStateHandle()) } }
        awaitTrue("catalog") { vm.uiState.value.allProducts.any { it.name == "Kopi Uji" } }
        awaitTrue("server printer config") { koin.get<PrinterSettingsRepository>().getConfig().hasCashierPrinter }

        LiveStack.setApiReachable(false)
        val kopi = vm.uiState.value.allProducts.first { it.name == "Kopi Uji" }
        onMain { vm.addProductToCart(kopi) }
        awaitTrue("offline quote") {
            val s = vm.uiState.value
            s.isOffline && s.quote?.quoteFingerprint == OfflinePricing.OFFLINE_FINGERPRINT
        }
        val estimate = vm.uiState.value.quote!!.totalAmount

        onMain { vm.submitCashPayment(tenderedAmount = 50_000L, drawerId = drawer.id, shiftId = shiftId) }
        awaitTrue("offline sale queued") { vm.uiState.value.cart.isEmpty() && !vm.uiState.value.isSubmittingOrder }
        val slip = awaitJob { "OFFLINE" in it }
        assertTrue("the slip says the official figures follow", "ANGKA RESMI MENYUSUL" in slip)
        assertTrue("the slip prints the device estimate", rupiah(estimate) in slip)

        LiveStack.setApiReachable(true)
        val sync = koin.get<OfflineSyncCoordinator>()
        val queue = koin.get<OfflineOrderDao>()
        awaitTrue("order synced") {
            sync.syncPendingOrders()
            queue.getPendingOrders().isEmpty()
        }
        assertEquals("nothing left to send twice", 0, sync.syncPendingOrders())

        // Exactly one sale reached the server, priced by the server at the device's estimate.
        val x = LiveStack.api("GET", "/pos/shifts/$shiftId/x-report", managerToken)
        assertEquals(estimate, x.getJSONObject("summary").getLong("cash_sales"))
    }

    private fun rupiah(amount: Long) = "%,d".format(amount).replace(',', '.')

    private suspend fun awaitJob(match: (String) -> Boolean): String {
        var found = ""
        awaitTrue("printer job") {
            val jobs = JSONArray(LiveStack.http("GET", LiveStack.PRINTER_JOBS))
            found = (0 until jobs.length()).map { jobs.getJSONObject(it).getString("text") }.firstOrNull(match).orEmpty()
            found.isNotEmpty()
        }
        return found
    }

    private suspend fun awaitTrue(what: String, condition: suspend () -> Boolean) {
        try {
            withTimeout(TIMEOUT_MS) { while (!condition()) delay(300) }
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
        const val OPENING_FLOAT = 100_000L
        const val TIMEOUT_MS = 20_000L
    }
}
