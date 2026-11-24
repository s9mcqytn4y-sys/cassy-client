package com.azuredevid.cassy.live

import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.azuredevid.cassy.data.network.CassyRealtimeClient
import com.azuredevid.cassy.data.network.RealtimeConnectionState
import com.azuredevid.cassy.domain.model.KdsStation
import com.azuredevid.cassy.domain.model.KdsTicket
import com.azuredevid.cassy.domain.model.KdsTicketStatus
import com.azuredevid.cassy.domain.repository.KdsRepository
import com.azuredevid.cassy.ui.kds.KdsUiState
import com.azuredevid.cassy.ui.kds.KdsViewModel
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
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
import java.util.UUID

/**
 * S3 KDS against the Docker test stack (OPS-06, PrdOpsAndPwaClose.md section 11). The app is the
 * kitchen tablet (`kitchen.e2e`); the cashier sends tab rounds over plain HTTP through the proxy the
 * tests never cut, so the board's own session and WebSocket stay untouched.
 */
@RunWith(AndroidJUnit4::class)
class LiveKdsTest {

    private val koin get() = GlobalContext.get()

    private lateinit var cashierToken: String
    private lateinit var nasiId: String
    private lateinit var kopiId: String
    private lateinit var tabId: String
    private var board: KdsViewModel? = null

    @Before
    fun start(): Unit = runBlocking {
        LiveStack.startAsKitchen()
        cashierToken = LiveStack.staffToken("cashier.e2e@cassy.local", LiveStack.CASHIER_PIN)
        val products = LiveStack.api("GET", "/pos/catalog", cashierToken).getJSONArray("products").objects()
        nasiId = products.first { it.getString("name") == "Nasi Uji" }.getString("id")
        kopiId = products.first { it.getString("name") == "Kopi Uji" }.getString("id")
        val tab = LiveStack.api(
            "POST", "/pos/tabs", cashierToken,
            JSONObject().put("idempotency_key", uuid()).put("table_number", "KDS").put("allow_duplicate_label", true)
        )
        tabId = tab.getJSONObject("order").getString("id")
    }

    @After
    fun stop() {
        board?.viewModelScope?.cancel()
        koin.get<CassyRealtimeClient>().stop()
        LiveStack.setApiReachable(true)
    }

    @Test
    fun kitchenSeesOnlyItsTicketsBumpsRecallsAndCannotTouchTheBar(): Unit = runBlocking {
        val vm = openBoard()
        assertEquals(KdsStation.KITCHEN, vm.uiState.value.station)

        val round = sendRound(nasiId, kopiId)
        val ticket = awaitBoard(vm, TIMEOUT) { s -> s.find(round) != null }.find(round)!!
        assertTrue(
            "the kitchen board shows kitchen tickets only",
            vm.uiState.value.tickets.all { it.station == KdsStation.KITCHEN }
        )

        // The bar ticket of the same round is out of the kitchen's reach (S3-Q3).
        val barTicket = LiveStack.api("GET", "/kds/tickets?station=bar", cashierToken).getJSONArray("tickets").objects()
            .first { it.getString("order_id") == tabId && it.optInt("round_number") == round }
        val refused = koin.get<KdsRepository>()
            .transition(barTicket.getString("id"), KdsTicketStatus.PREPARING, expected = KdsTicketStatus.QUEUED)
        assertTrue("kitchen must not bump a bar ticket", refused.isFailure)

        vm.bump(ticket.id)
        awaitStatus(vm, ticket.id, KdsTicketStatus.PREPARING)
        vm.bump(ticket.id)
        awaitStatus(vm, ticket.id, KdsTicketStatus.READY)
        vm.recall(ticket.id)
        awaitStatus(vm, ticket.id, KdsTicketStatus.PREPARING)
        vm.bump(ticket.id)
        awaitStatus(vm, ticket.id, KdsTicketStatus.READY)
        vm.bump(ticket.id)
        awaitBoard(vm, TIMEOUT) { s -> s.tickets.none { it.id == ticket.id } }

        val barAfter = LiveStack.api("GET", "/kds/tickets?station=bar", cashierToken).getJSONArray("tickets").objects()
            .first { it.getString("id") == barTicket.getString("id") }
        assertEquals("the refused bump left the bar ticket alone", "queued", barAfter.getString("status"))
    }

    /** S3-Q1: send to board over the live WebSocket, p95 under 2 s across [LATENCY_SAMPLES] rounds. */
    @Test
    fun ticketReachesTheBoardWithinTwoSecondsAtP95(): Unit = runBlocking {
        val vm = openBoard()
        val samples = (1..LATENCY_SAMPLES).map {
            val sentAt = SystemClock.elapsedRealtime()
            val round = sendRound(nasiId)
            awaitBoard(vm, TIMEOUT) { s -> s.find(round) != null }
            SystemClock.elapsedRealtime() - sentAt
        }
        val p95 = samples.sorted()[(samples.size * 95 + 99) / 100 - 1]
        Log.i(GATE_TAG, JSONObject().put("gate", "kds_latency").put("p95_ms", p95).put("samples_ms", JSONArray(samples)).toString())
        assertTrue("KDS latency p95 ${p95}ms (samples $samples) must stay under ${LATENCY_P95_MS}ms", p95 < LATENCY_P95_MS)
    }

    /** S3-Q2: tickets sent while the kitchen tablet is cut off appear once it reconnects, once each. */
    @Test
    fun ticketsSentDuringAnOutageAppearAfterReconnectWithoutDuplicates(): Unit = runBlocking {
        val vm = openBoard()
        val cutAt = SystemClock.elapsedRealtime()
        LiveStack.setApiReachable(false)
        awaitBoard(vm, TIMEOUT) { it.connection != RealtimeConnectionState.CONNECTED }
        val rounds = listOf(sendRound(nasiId), sendRound(nasiId))
        delay((OUTAGE_MS - (SystemClock.elapsedRealtime() - cutAt)).coerceAtLeast(0L))

        LiveStack.setApiReachable(true)
        val restoredAt = SystemClock.elapsedRealtime()
        // The reconnect backoff is capped at 30 s; the gate is what happens once the socket is back.
        awaitBoard(vm, RECONNECT_TIMEOUT) { it.connection == RealtimeConnectionState.CONNECTED }
        val connectedAt = SystemClock.elapsedRealtime()
        val state = awaitBoard(vm, AFTER_RECONNECT_MS) { s -> rounds.all { s.find(it) != null } }
        val visibleAt = SystemClock.elapsedRealtime()

        Log.i(
            GATE_TAG,
            JSONObject().put("gate", "kds_reconnect").put("restore_to_connected_ms", connectedAt - restoredAt)
                .put("connected_to_visible_ms", visibleAt - connectedAt).toString()
        )
        assertEquals("each ticket shows once", state.tickets.size, state.tickets.map { it.id }.toSet().size)
    }

    private suspend fun openBoard(): KdsViewModel {
        val vm = koin.get<KdsViewModel> { parametersOf(SavedStateHandle()) }
        board = vm
        awaitBoard(vm, RECONNECT_TIMEOUT) { !it.isLoading && it.connection == RealtimeConnectionState.CONNECTED }
        return vm
    }

    /** Sends one tab round as the cashier and returns its round number. */
    private fun sendRound(vararg productIds: String): Int {
        val items = JSONArray(productIds.map { JSONObject().put("product_id", it).put("quantity", 1) })
        val body = JSONObject().put("idempotency_key", uuid()).put("items", items)
        return LiveStack.api("POST", "/pos/tabs/$tabId/rounds", cashierToken, body).getJSONObject("round").getInt("round_number")
    }

    private fun KdsUiState.find(round: Int): KdsTicket? = tickets.firstOrNull { it.orderId == tabId && it.roundNumber == round }

    private suspend fun awaitStatus(vm: KdsViewModel, id: String, status: KdsTicketStatus) {
        awaitBoard(vm, TIMEOUT) { s -> s.tickets.any { it.id == id && it.status == status } && id !in s.pendingTicketIds }
    }

    private suspend fun awaitBoard(vm: KdsViewModel, timeoutMs: Long, until: (KdsUiState) -> Boolean): KdsUiState =
        withTimeout(timeoutMs) { vm.uiState.first(until) }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

    private fun uuid() = UUID.randomUUID().toString()

    private companion object {
        /** Read by the live workflow into `KdsLatency.json`. */
        const val GATE_TAG = "CassyKdsGate"
        const val LATENCY_SAMPLES = 20
        const val LATENCY_P95_MS = 2_000L
        const val OUTAGE_MS = 5_000L
        const val AFTER_RECONNECT_MS = 5_000L
        const val TIMEOUT = 10_000L
        const val RECONNECT_TIMEOUT = 40_000L
    }
}
