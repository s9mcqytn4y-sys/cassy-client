package com.azuredevid.cassy.ui.kds

import androidx.datastore.core.DataStore
import com.azuredevid.cassy.R
import com.azuredevid.cassy.core.security.DataStoreSessionManager
import com.azuredevid.cassy.data.network.RealtimeConnectionState
import com.azuredevid.cassy.data.network.RealtimeEventEnvelope
import com.azuredevid.cassy.domain.error.DomainError
import com.azuredevid.cassy.domain.error.DomainException
import com.azuredevid.cassy.domain.model.KdsStation
import com.azuredevid.cassy.domain.model.KdsTicket
import com.azuredevid.cassy.domain.model.KdsTicketStatus
import com.azuredevid.cassy.domain.model.PaymentCredentialInput
import com.azuredevid.cassy.domain.model.PaymentCredentials
import com.azuredevid.cassy.domain.model.QRServiceMode
import com.azuredevid.cassy.domain.model.StaffIdentity
import com.azuredevid.cassy.domain.model.StoreCMSConfig
import com.azuredevid.cassy.domain.model.StoreCMSConfigInput
import com.azuredevid.cassy.domain.model.StoreSettings
import com.azuredevid.cassy.domain.model.StoreSettingsPatch
import com.azuredevid.cassy.domain.model.StoredSession
import com.azuredevid.cassy.domain.repository.KdsRepository
import com.azuredevid.cassy.domain.repository.RealtimeEventSource
import com.azuredevid.cassy.domain.repository.StoreGovernanceRepository
import com.azuredevid.cassy.ui.designsystem.CassyToastTone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class KdsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private class InMemorySessionDataStore : DataStore<StoredSession> {
        private val state = MutableStateFlow(StoredSession())
        private val mutex = Mutex()
        override val data: Flow<StoredSession> = state
        override suspend fun updateData(transform: suspend (t: StoredSession) -> StoredSession): StoredSession =
            mutex.withLock { transform(state.value).also { state.value = it } }
    }

    private class FakeRealtime : RealtimeEventSource {
        override val connectionState = MutableStateFlow(RealtimeConnectionState.CONNECTING)
        override val events = MutableSharedFlow<RealtimeEventEnvelope>(extraBufferCapacity = 16)
        var started = 0
        override fun start(scope: CoroutineScope) { started++ }
    }

    private class FakeSettings(var mode: QRServiceMode = QRServiceMode.TABLE) : StoreGovernanceRepository {
        override suspend fun getStoreSettings(): Result<StoreSettings> =
            Result.success(StoreSettings(mode, false, 0, 0, "off", false))
        override suspend fun getPaymentCredentials(): Result<PaymentCredentials> = Result.failure(UnsupportedOperationException())
        override suspend fun savePaymentCredentials(input: PaymentCredentialInput): Result<PaymentCredentials> = Result.failure(UnsupportedOperationException())
        override suspend fun getStoreCMS(): Result<StoreCMSConfig> = Result.failure(UnsupportedOperationException())
        override suspend fun saveStoreCMS(input: StoreCMSConfigInput): Result<StoreCMSConfig> = Result.failure(UnsupportedOperationException())
        override suspend fun updateStoreSettings(patch: StoreSettingsPatch): Result<StoreSettings> = Result.failure(UnsupportedOperationException())
    }

    private class FakeKds : KdsRepository {
        val server = mutableMapOf<String, KdsTicket>()
        val listCalls = mutableListOf<KdsStation>()
        val transitions = mutableListOf<Triple<String, KdsTicketStatus, KdsTicketStatus>>()
        var transitionError: Throwable? = null

        override suspend fun listTickets(station: KdsStation): Result<List<KdsTicket>> {
            listCalls += station
            return Result.success(server.values.filter { it.station == station && it.status.isOpen })
        }

        override suspend fun transition(ticketId: String, to: KdsTicketStatus, expected: KdsTicketStatus): Result<KdsTicket> {
            transitions += Triple(ticketId, to, expected)
            transitionError?.let { return Result.failure(it) }
            val current = server.getValue(ticketId)
            if (current.status != expected) {
                return Result.failure(DomainException(DomainError.Conflict("Tiket sudah diubah.", KdsRepository.CODE_STALE_STATUS)))
            }
            return Result.success(current.copy(status = to).also { server[ticketId] = it })
        }
    }

    private lateinit var kds: FakeKds
    private lateinit var realtime: FakeRealtime
    private val effects = mutableListOf<KdsUiEffect>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        kds = FakeKds().apply {
            server["b-1"] = ticket("b-1", KdsStation.BAR, KdsTicketStatus.QUEUED, createdAt = 2_000L)
            server["b-0"] = ticket("b-0", KdsStation.BAR, KdsTicketStatus.READY, createdAt = 1_000L)
            server["k-1"] = ticket("k-1", KdsStation.KITCHEN, KdsTicketStatus.PREPARING, createdAt = 1_500L)
        }
        realtime = FakeRealtime()
        effects.clear()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun TestScope.viewModel(
        permissions: List<String>? = listOf("kds.ticket.read.barista", "kds.ticket.update.barista"),
        settings: FakeSettings = FakeSettings()
    ): KdsViewModel {
        val session = DataStoreSessionManager(InMemorySessionDataStore(), backgroundScope)
        if (permissions != null) {
            session.saveLoginSession(
                accessToken = "jwt", accessTokenExpiresAt = "2026-11-17T00:00:00Z", sessionExpiresAt = "2026-11-17T00:00:00Z",
                staff = StaffIdentity("u1", "Barista", "bar@cassy.test", "barista", "s1", "kopi", "Kopi"),
                permissions = permissions
            )
        }
        val vm = KdsViewModel(kds, session, realtime, settings, clock = flowOf(NOW))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.effects.collect { effects += it } }
        testDispatcher.scheduler.advanceUntilIdle()
        return vm
    }

    private fun lastMessage() = effects.filterIsInstance<KdsUiEffect.ShowMessage>().last()

    private fun event(type: String, seq: Long, station: String?) = RealtimeEventEnvelope(
        seq = seq, type = type, storeId = "s1",
        payload = JsonObject(buildMap { if (station != null) put("station", JsonPrimitive(station)) })
    )

    @Test
    fun noKdsPermission_failsClosedWithoutLoadingOrConnecting() = runTest(testDispatcher) {
        val vm = viewModel(permissions = listOf("order.pos.create"))

        assertNull(vm.uiState.value.station)
        assertEquals(KdsMessage.Res(R.string.kds_error_no_station), vm.uiState.value.loadError)
        assertTrue(kds.listCalls.isEmpty())
        assertEquals(0, realtime.started)
    }

    @Test
    fun baristaSeesOnlyBarTicketsOldestFirst() = runTest(testDispatcher) {
        val vm = viewModel()

        val state = vm.uiState.value
        assertEquals(listOf(KdsStation.BAR), state.availableStations)
        assertEquals(KdsStation.BAR, state.station)
        assertTrue(state.canBump)
        assertEquals(listOf("b-0", "b-1"), state.tickets.map { it.id })
        assertEquals(1, realtime.started)
        assertEquals(QRServiceMode.TABLE, state.serviceMode)
        assertEquals(NOW, state.nowMillis)
    }

    @Test
    fun readOnlyStaffSeesTicketsButCannotBump() = runTest(testDispatcher) {
        val vm = viewModel(permissions = listOf("kds.ticket.read.kitchen"))

        assertFalse(vm.uiState.value.canBump)
        vm.bump("k-1")
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(kds.transitions.isEmpty())
    }

    @Test
    fun bump_walksQueuedToServedAndServedLeavesBoard() = runTest(testDispatcher) {
        val vm = viewModel()

        vm.bump("b-1")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(KdsTicketStatus.PREPARING, vm.uiState.value.tickets.first { it.id == "b-1" }.status)
        vm.bump("b-1")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(KdsTicketStatus.READY, vm.uiState.value.tickets.first { it.id == "b-1" }.status)
        vm.bump("b-1")
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.uiState.value.tickets.none { it.id == "b-1" })
        assertEquals(
            listOf(
                Triple("b-1", KdsTicketStatus.PREPARING, KdsTicketStatus.QUEUED),
                Triple("b-1", KdsTicketStatus.READY, KdsTicketStatus.PREPARING),
                Triple("b-1", KdsTicketStatus.SERVED, KdsTicketStatus.READY)
            ),
            kds.transitions
        )
    }

    @Test
    fun recall_movesReadyBackToPreparingOnly() = runTest(testDispatcher) {
        val vm = viewModel()

        vm.recall("b-1") // queued: ignored
        vm.recall("b-0")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(Triple("b-0", KdsTicketStatus.PREPARING, KdsTicketStatus.READY)), kds.transitions)
        assertEquals(KdsTicketStatus.PREPARING, vm.uiState.value.tickets.first { it.id == "b-0" }.status)
    }

    @Test
    fun doubleTapWhilePendingSendsOneTransition() = runTest(testDispatcher) {
        val vm = viewModel()

        vm.bump("b-1")
        vm.bump("b-1")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, kds.transitions.size)
        assertTrue(vm.uiState.value.pendingTicketIds.isEmpty())
    }

    @Test
    fun staleStatus409_reloadsBoardWithServerTruthAndWarns() = runTest(testDispatcher) {
        val vm = viewModel()
        val loadsBefore = kds.listCalls.size
        // Another tablet already started this ticket.
        kds.server["b-1"] = kds.server.getValue("b-1").copy(status = KdsTicketStatus.PREPARING)

        vm.bump("b-1") // still sends expected=queued from the stale board
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(Triple("b-1", KdsTicketStatus.PREPARING, KdsTicketStatus.QUEUED), kds.transitions.single())
        assertEquals(loadsBefore + 1, kds.listCalls.size)
        assertEquals(KdsTicketStatus.PREPARING, vm.uiState.value.tickets.first { it.id == "b-1" }.status)
        assertEquals(KdsMessage.Res(R.string.kds_msg_stale_reloaded), lastMessage().message)
        assertEquals(CassyToastTone.WARNING, lastMessage().tone)
    }

    @Test
    fun otherTransitionError_showsMessageWithoutReload() = runTest(testDispatcher) {
        val vm = viewModel()
        val loadsBefore = kds.listCalls.size
        kds.transitionError = DomainException(DomainError.NetworkUnavailable("Koneksi ke server terputus."))

        vm.bump("b-1")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(loadsBefore, kds.listCalls.size)
        assertEquals(KdsMessage.Server("Koneksi ke server terputus."), lastMessage().message)
        assertEquals(KdsTicketStatus.QUEUED, vm.uiState.value.tickets.first { it.id == "b-1" }.status)
    }

    @Test
    fun realtime_reloadsOnlyForThisStationAndOnResync() = runTest(testDispatcher) {
        val vm = viewModel()
        val base = kds.listCalls.size

        realtime.events.emit(event("kds.ticket_updated", 5, "kitchen"))
        realtime.events.emit(event("order.paid", 6, null))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("other station and non-KDS events are ignored", base, kds.listCalls.size)

        kds.server["b-2"] = ticket("b-2", KdsStation.BAR, KdsTicketStatus.QUEUED, createdAt = 3_000L)
        realtime.events.emit(event("kds.ticket_created", 7, "bar"))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(base + 1, kds.listCalls.size)
        assertTrue(vm.uiState.value.tickets.any { it.id == "b-2" })

        realtime.events.emit(event(RealtimeEventEnvelope.TYPE_RESYNC_REQUIRED, 0, null))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(base + 2, kds.listCalls.size)
    }

    @Test
    fun realtime_burstOfEventsIsConflatedIntoFewReloads() = runTest(testDispatcher) {
        viewModel()
        val base = kds.listCalls.size

        repeat(20) { realtime.events.emit(event("kds.ticket_updated", 100L + it, "bar")) }
        testDispatcher.scheduler.advanceUntilIdle()

        // Conflated channel: at most one reload waits while one runs, so a burst costs a handful of
        // reloads (the exact count depends on scheduling), never one per event.
        val reloads = kds.listCalls.size - base
        assertTrue("20 events must not cost 20 reloads, got $reloads", reloads in 1..5)
    }

    @Test
    fun reconnect_triggersCatchUpReloadAndClearsOfflineBanner() = runTest(testDispatcher) {
        val vm = viewModel()
        assertTrue(vm.uiState.value.isOffline)
        val base = kds.listCalls.size

        realtime.connectionState.value = RealtimeConnectionState.CONNECTED
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(vm.uiState.value.isOffline)
        assertEquals(base + 1, kds.listCalls.size)

        realtime.connectionState.value = RealtimeConnectionState.RECONNECTING
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.isOffline)
        realtime.connectionState.value = RealtimeConnectionState.CONNECTED
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(base + 2, kds.listCalls.size)
    }

    @Test
    fun selectStation_ignoresStationsWithoutPermission() = runTest(testDispatcher) {
        val vm = viewModel()

        vm.selectStation(KdsStation.KITCHEN)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(KdsStation.BAR, vm.uiState.value.station)
        assertTrue(kds.listCalls.none { it == KdsStation.KITCHEN })
    }

    @Test
    fun selectStation_switchesBoardForDualStationStaff() = runTest(testDispatcher) {
        val vm = viewModel(
            permissions = listOf("kds.ticket.read.barista", "kds.ticket.read.kitchen", "kds.ticket.update.kitchen")
        )
        assertFalse("no bar update permission", vm.uiState.value.canBump)

        vm.selectStation(KdsStation.KITCHEN)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(KdsStation.KITCHEN, vm.uiState.value.station)
        assertTrue(vm.uiState.value.canBump)
        assertEquals(listOf("k-1"), vm.uiState.value.tickets.map { it.id })
    }

    private companion object {
        const val NOW = 1_800_000_000_000L

        fun ticket(id: String, station: KdsStation, status: KdsTicketStatus, createdAt: Long) = KdsTicket(
            id = id, orderId = "o-$id", orderNumber = "ORD-$id", queueNumber = "A-$id", tableNumber = "3",
            roundNumber = null, station = station, status = status, latePayment = false,
            createdAtMillis = createdAt, lines = emptyList()
        )
    }
}
