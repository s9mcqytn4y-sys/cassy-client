package com.azuredevid.cassy.live

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.azuredevid.cassy.domain.model.CashDrawer
import com.azuredevid.cassy.data.network.RealtimeConnectionState
import com.azuredevid.cassy.domain.model.CreatePosOrderItemInput
import com.azuredevid.cassy.domain.model.InitiatePosPaymentInput
import com.azuredevid.cassy.domain.repository.CatalogRepository
import com.azuredevid.cassy.domain.repository.PosOrderRepository
import com.azuredevid.cassy.domain.repository.PrinterSettingsRepository
import com.azuredevid.cassy.domain.repository.RealtimeEventSource
import com.azuredevid.cassy.domain.repository.ShiftRepository
import com.azuredevid.cassy.domain.repository.TableTabRepository
import com.azuredevid.cassy.ui.pos.PosViewModel
import com.azuredevid.cassy.ui.pos.TabItemVoidViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.parameter.parametersOf
import java.util.UUID

/**
 * S5 close-gate evidence (PrdOpsAndPwaClose.md section 13) for the two rows the earlier live tests did not
 * cover: OPS-02 QRIS at the counter (charged at the fake Midtrans, settled by its signed webhook) and OPS-05
 * remote approval delivered over the POS WebSocket as `approval.decided`.
 */
@RunWith(AndroidJUnit4::class)
class LiveGateEvidenceTest {

    private val koin get() = GlobalContext.get()

    private lateinit var drawer: CashDrawer
    private lateinit var managerToken: String
    private lateinit var vm: PosViewModel
    private var shiftId: String? = null

    @Before
    fun openShift(): Unit = runBlocking {
        drawer = LiveStack.startOnDrawer("Laci Uji 1")
        managerToken = LiveStack.staffToken("manager.e2e@cassy.local", LiveStack.MANAGER_PIN)
        shiftId = koin.get<ShiftRepository>().openShift(openingFloat = 100_000L, drawerId = drawer.id).getOrThrow().shift.id
        LiveStack.http("DELETE", LiveStack.PRINTER_JOBS)
        vm = onMain { koin.get<PosViewModel> { parametersOf(SavedStateHandle()) } }
        awaitTrue("catalog") { vm.uiState.value.allProducts.any { it.name == "Kopi Uji" } }
    }

    @After
    fun closeShift() {
        shiftId?.let { id ->
            runCatching { LiveStack.api("POST", "/pos/shifts/$id/force-close", managerToken, JSONObject().put("reason", "Akhir uji gate S5")) }
        }
    }

    @Test
    fun counterQrisIsChargedSettledByWebhookAndPrinted(): Unit = runBlocking {
        awaitTrue("server printer config") { koin.get<PrinterSettingsRepository>().getConfig().hasCashierPrinter }
        val kopi = vm.uiState.value.allProducts.first { it.name == "Kopi Uji" }
        onMain { vm.addProductToCart(kopi) }
        awaitTrue("server quote") { vm.uiState.value.quote != null && !vm.uiState.value.isQuoting }

        onMain { vm.startQrisPayment(drawerId = drawer.id) }
        awaitTrue("QRIS charge shown") { vm.uiState.value.activeQrisPayment != null }
        val attempt = vm.uiState.value.activeQrisPayment!!

        // The stub posts a webhook signed like Midtrans; the server marks the order paid, the POS follows.
        val settled = JSONObject(LiveStack.http("POST", "${LiveStack.HOST_URL}:9200/_stub/charges/${attempt.providerOrderId}/settle"))
        assertTrue("webhook accepted: $settled", settled.getInt("webhook_status") == 200)
        awaitTrue("POS sees the payment") {
            onMain { vm.refreshQrisStatus() }
            vm.uiState.value.activeQrisPayment == null && vm.uiState.value.cart.isEmpty()
        }
        val receipt = awaitJob { "QRIS" in it }
        assertTrue("the receipt prints the item", "Kopi Uji" in receipt)
    }

    @Test
    fun remoteVoidApprovalArrivesOverTheWebSocket(): Unit = runBlocking {
        // The POS opens /ws itself (it used to wait for the KDS screen), so the decision arrives as an event.
        val realtime = koin.get<RealtimeEventSource>()
        awaitTrue("POS WebSocket connected") { realtime.connectionState.value == RealtimeConnectionState.CONNECTED }

        val tabs = koin.get<TableTabRepository>()
        val products = koin.get<CatalogRepository>().getStaffCatalog().getOrThrow().products.associateBy { it.name }
        val table = tabs.listTables().getOrThrow().first { it.tableNumber == "U1" }
        val tab = tabs.openTab(UUID.randomUUID().toString(), table.id, table.tableNumber).getOrThrow()
        tabs.submitRound(
            tab.id, UUID.randomUUID().toString(),
            listOf(
                CreatePosOrderItemInput(products.getValue("Tumpeng Uji").id, 1),
                CreatePosOrderItemInput(products.getValue("Kopi Uji").id, 1)
            )
        ).getOrThrow()
        val line = tabs.getTab(tab.id).getOrThrow().items.first { it.snapshotName == "Tumpeng Uji" }

        val voidVm = onMain { koin.get<TabItemVoidViewModel>() }
        onMain {
            voidVm.open(tab.id, line, "Tumpeng Uji")
            voidVm.setReason("Salah input")
            voidVm.setPin(LiveStack.CASHIER_PIN)
            voidVm.requestOwnerApproval()
        }
        var approvalId = ""
        awaitTrue("approval pending for the manager") {
            val items = LiveStack.api("GET", "/approvals", managerToken).getJSONArray("items")
            approvalId = (0 until items.length()).map { items.getJSONObject(it) }.firstOrNull()?.getString("id").orEmpty()
            approvalId.isNotEmpty()
        }
        LiveStack.api("POST", "/approvals/$approvalId/approve", managerToken, JSONObject().put("pin", LiveStack.MANAGER_PIN))

        // Polling is off while the socket is connected, so the void can only complete through approval.decided.
        withTimeout(10_000L) { voidVm.voided.first() }
        assertTrue(
            "the voided item is gone from the tab",
            tabs.getTab(tab.id).getOrThrow().items.none { it.snapshotName == "Tumpeng Uji" && it.quantity > 0 }
        )

        // Settle what is left so table U1 is free for the next live test.
        val closed = tabs.closeTab(tab.id, splitCount = 1).getOrThrow()
        koin.get<PosOrderRepository>().initiatePayment(
            InitiatePosPaymentInput(
                orderId = tab.id,
                idempotencyKey = UUID.randomUUID().toString(),
                method = "cash",
                cashTenderedAmount = closed.order.totalAmount + 1_000L,
                drawerId = drawer.id
            )
        ).getOrThrow()
    }

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
            withTimeout(30_000L) { while (!condition()) delay(500) }
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
}
