package com.azuredevid.cassy.ui.kds

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.azuredevid.cassy.R
import com.azuredevid.cassy.core.security.SessionManager
import com.azuredevid.cassy.data.network.RealtimeConnectionState
import com.azuredevid.cassy.data.network.RealtimeEventEnvelope
import com.azuredevid.cassy.domain.error.DomainError
import com.azuredevid.cassy.domain.error.DomainException
import com.azuredevid.cassy.domain.model.KdsStation
import com.azuredevid.cassy.domain.model.KdsTicket
import com.azuredevid.cassy.domain.model.KdsTicketStatus
import com.azuredevid.cassy.domain.model.QRServiceMode
import com.azuredevid.cassy.domain.repository.KdsRepository
import com.azuredevid.cassy.domain.repository.RealtimeEventSource
import com.azuredevid.cassy.domain.repository.StoreGovernanceRepository
import com.azuredevid.cassy.ui.designsystem.CassyToastTone
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Snackbar text: a string resource, or text the backend already wrote for the user. */
sealed interface KdsMessage {
    data class Res(@StringRes val id: Int) : KdsMessage
    data class Server(val text: String) : KdsMessage
}

/** KDS screen state. Tickets are open ones only, oldest first, so the longest wait is always first. */
@Immutable
data class KdsUiState(
    val availableStations: List<KdsStation> = emptyList(),
    val station: KdsStation? = null,
    val tickets: List<KdsTicket> = emptyList(),
    val canBump: Boolean = false,
    val serviceMode: QRServiceMode = QRServiceMode.PICKUP,
    val connection: RealtimeConnectionState = RealtimeConnectionState.DISCONNECTED,
    val nowMillis: Long = 0L,
    val pendingTicketIds: Set<String> = emptySet(),
    val isLoading: Boolean = true,
    val loadError: KdsMessage? = null
) {
    fun ticketsIn(status: KdsTicketStatus): List<KdsTicket> = tickets.filter { it.status == status }

    /** Live updates are not flowing; the board may be behind until the connection returns. */
    val isOffline: Boolean
        get() = connection != RealtimeConnectionState.CONNECTED
}

sealed interface KdsUiEffect {
    data class ShowMessage(val message: KdsMessage, val tone: CassyToastTone) : KdsUiEffect
}

/**
 * Kitchen Display System for one station at a time (Release One PR 8, US3).
 *
 * - Stations come from the session's `kds.ticket.read.*` permissions; none means no access (fail closed).
 * - Live updates come from [realtime]; any `kds.*` event for this station, a `resync_required`, or a
 *   reconnect triggers a REST reload. Reload requests are conflated, so a burst of events costs one call.
 * - Bumps are server-confirmed with `expected_status`. A 409 `KDS_STALE_STATUS` (or
 *   `KDS_TRANSITION_NOT_ALLOWED`) means another screen moved the ticket: the board reloads, nothing retries.
 * - [clock] ticks the age display; tests inject a finite flow.
 */
class KdsViewModel(
    private val repository: KdsRepository,
    private val sessionManager: SessionManager,
    private val realtime: RealtimeEventSource,
    private val storeSettings: StoreGovernanceRepository,
    private val clock: Flow<Long> = tickingClock(),
    private val savedStateHandle: SavedStateHandle = SavedStateHandle()
) : ViewModel() {

    private val _uiState = MutableStateFlow(KdsUiState())
    val uiState: StateFlow<KdsUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<KdsUiEffect>()
    val effects: SharedFlow<KdsUiEffect> = _effects.asSharedFlow()

    private val reloadRequests = Channel<Unit>(Channel.CONFLATED)

    init {
        val permissions = sessionManager.currentSession()?.takeIf { it.staff != null }?.permissions.orEmpty()
        val stations = KdsStation.entries.filter { it.readPermission in permissions }
        val saved = savedStateHandle.get<String>(KEY_STATION)?.let(KdsStation::fromWire)
        val station = saved?.takeIf { it in stations } ?: stations.firstOrNull()
        _uiState.update {
            it.copy(
                availableStations = stations,
                station = station,
                canBump = station != null && station.updatePermission in permissions,
                isLoading = station != null,
                loadError = if (station == null) KdsMessage.Res(R.string.kds_error_no_station) else null
            )
        }
        if (station != null) {
            viewModelScope.launch { for (request in reloadRequests) load() }
            viewModelScope.launch { clock.collect { now -> _uiState.update { it.copy(nowMillis = now) } } }
            viewModelScope.launch { loadServiceMode() }
            observeRealtime()
            requestReload()
        }
    }

    /** Switches station and reloads; ignored for a station the session cannot read. */
    fun selectStation(station: KdsStation) {
        val state = _uiState.value
        if (station !in state.availableStations || station == state.station) return
        val permissions = sessionManager.currentSession()?.permissions.orEmpty()
        savedStateHandle[KEY_STATION] = station.wireValue
        _uiState.update {
            it.copy(station = station, tickets = emptyList(), isLoading = true, canBump = station.updatePermission in permissions)
        }
        requestReload()
    }

    fun refresh() = requestReload()

    /** Moves the ticket one step forward (queued to preparing to ready to served). */
    fun bump(ticketId: String) {
        val ticket = _uiState.value.tickets.firstOrNull { it.id == ticketId } ?: return
        val target = ticket.status.next ?: return
        transition(ticket, target)
    }

    /** Sends a ready ticket back to preparing (for example a remade drink). */
    fun recall(ticketId: String) {
        val ticket = _uiState.value.tickets.firstOrNull { it.id == ticketId } ?: return
        if (ticket.status != KdsTicketStatus.READY) return
        transition(ticket, KdsTicketStatus.PREPARING)
    }

    private fun transition(ticket: KdsTicket, target: KdsTicketStatus) {
        val state = _uiState.value
        if (!state.canBump || ticket.id in state.pendingTicketIds || !ticket.status.canMoveTo(target)) return
        _uiState.update { it.copy(pendingTicketIds = it.pendingTicketIds + ticket.id) }
        viewModelScope.launch {
            repository.transition(ticket.id, target, expected = ticket.status).fold(
                onSuccess = { updated ->
                    _uiState.update { s ->
                        val others = s.tickets.filterNot { it.id == updated.id }
                        s.copy(
                            pendingTicketIds = s.pendingTicketIds - ticket.id,
                            tickets = if (updated.status.isOpen) sortOldestFirst(others + updated) else others
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(pendingTicketIds = it.pendingTicketIds - ticket.id) }
                    if (err.isStaleConflict()) {
                        emit(KdsMessage.Res(R.string.kds_msg_stale_reloaded), CassyToastTone.WARNING)
                        requestReload()
                    } else {
                        emit(err.toKdsMessage(R.string.kds_error_transition), CassyToastTone.ERROR)
                    }
                }
            )
        }
    }

    private suspend fun load() {
        val station = _uiState.value.station ?: return
        repository.listTickets(station).fold(
            onSuccess = { tickets ->
                // A reply for a station the user already left is discarded.
                if (_uiState.value.station != station) return
                _uiState.update {
                    it.copy(isLoading = false, loadError = null, tickets = sortOldestFirst(tickets.filter { t -> t.status.isOpen }))
                }
            },
            onFailure = { err ->
                _uiState.update { it.copy(isLoading = false, loadError = err.toKdsMessage(R.string.kds_error_load)) }
            }
        )
    }

    private suspend fun loadServiceMode() {
        storeSettings.getStoreSettings().onSuccess { settings ->
            _uiState.update { it.copy(serviceMode = settings.qrServiceMode) }
        }
    }

    private fun observeRealtime() {
        realtime.start(viewModelScope)
        viewModelScope.launch {
            var previous = realtime.connectionState.value
            realtime.connectionState.collect { state ->
                _uiState.update { it.copy(connection = state) }
                // Replay covers short gaps, but a reload after reconnecting also covers a ticket that
                // changed while the replay window was exceeded or the app was paused.
                if (state == RealtimeConnectionState.CONNECTED && previous != RealtimeConnectionState.CONNECTED) requestReload()
                previous = state
            }
        }
        viewModelScope.launch {
            realtime.events.collect { event -> if (concernsBoard(event)) requestReload() }
        }
    }

    private fun concernsBoard(event: RealtimeEventEnvelope): Boolean {
        if (event.type == RealtimeEventEnvelope.TYPE_RESYNC_REQUIRED) return true
        if (!event.type.startsWith("kds.")) return false
        val eventStation = event.payloadString("station") ?: return true
        return eventStation == _uiState.value.station?.wireValue
    }

    private fun requestReload() {
        reloadRequests.trySend(Unit)
    }

    private fun emit(message: KdsMessage, tone: CassyToastTone) {
        viewModelScope.launch { _effects.emit(KdsUiEffect.ShowMessage(message, tone)) }
    }

    private fun Throwable.isStaleConflict(): Boolean {
        val error = (this as? DomainException)?.error as? DomainError.Conflict ?: return false
        return error.code == KdsRepository.CODE_STALE_STATUS || error.code == CODE_TRANSITION_NOT_ALLOWED
    }

    private fun Throwable.toKdsMessage(@StringRes fallback: Int): KdsMessage =
        (this as? DomainException)?.error?.message?.takeIf { it.isNotBlank() }?.let { KdsMessage.Server(it) }
            ?: KdsMessage.Res(fallback)

    companion object {
        private const val KEY_STATION = "kds_station"
        private const val CODE_TRANSITION_NOT_ALLOWED = "KDS_TRANSITION_NOT_ALLOWED"
        private const val CLOCK_TICK_MILLIS = 15_000L

        private fun sortOldestFirst(tickets: List<KdsTicket>) = tickets.sortedBy { it.createdAtMillis }

        /** Wall clock that ticks every 15 s, enough for minute-level age labels. */
        fun tickingClock(): Flow<Long> = flow {
            while (currentCoroutineContext().isActive) {
                emit(System.currentTimeMillis())
                delay(CLOCK_TICK_MILLIS)
            }
        }
    }
}
