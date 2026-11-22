package com.azuredevid.cassy.ui.pos

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.azuredevid.cassy.R
import com.azuredevid.cassy.data.network.RealtimeConnectionState
import com.azuredevid.cassy.data.network.RealtimeEventEnvelope
import com.azuredevid.cassy.domain.repository.RealtimeEventSource
import com.azuredevid.cassy.domain.repository.ServiceRequest
import com.azuredevid.cassy.domain.repository.ServiceRequestRepository
import com.azuredevid.cassy.ui.designsystem.UiText
import com.azuredevid.cassy.ui.designsystem.toUiText
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Immutable
data class ServiceRequestUiState(
    val requests: List<ServiceRequest> = emptyList(),
    val isPanelOpen: Boolean = false,
    val busyRequestId: String? = null
)

/**
 * Guest "Panggil Pelayan" queue for the cashier. The list reloads on every `service_request.*`
 * realtime event and falls back to periodic polling while the socket is disconnected.
 */
class ServiceRequestViewModel(
    private val repository: ServiceRequestRepository,
    private val realtimeClient: RealtimeEventSource? = null,
    private val fallbackPollIntervalMs: Long = 15_000L
) : ViewModel() {

    private val _uiState = MutableStateFlow(ServiceRequestUiState())
    val uiState: StateFlow<ServiceRequestUiState> = _uiState.asStateFlow()

    private val _errors = Channel<UiText>(Channel.BUFFERED)
    val errors = _errors.receiveAsFlow()

    private var fallbackPollJob: Job? = null

    init {
        refresh()
        realtimeClient?.let { client ->
            viewModelScope.launch {
                client.events.collect {
                    if (it.type.startsWith("service_request") || it.type == RealtimeEventEnvelope.TYPE_RESYNC_REQUIRED) {
                        refresh()
                    }
                }
            }
            viewModelScope.launch {
                client.connectionState.collect { state ->
                    if (state == RealtimeConnectionState.CONNECTED) {
                        fallbackPollJob?.cancel()
                        fallbackPollJob = null
                    } else if (fallbackPollJob == null || fallbackPollJob?.isActive != true) {
                        fallbackPollJob = launch {
                            while (isActive) {
                                delay(fallbackPollIntervalMs)
                                refresh()
                            }
                        }
                    }
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            repository.listActive()
                .onSuccess { list -> _uiState.update { it.copy(requests = list) } }
                .onFailure { _errors.send(it.toUiText(R.string.service_msg_load_failed)) }
        }
    }

    fun openPanel() {
        _uiState.update { it.copy(isPanelOpen = true) }
        refresh()
    }

    fun closePanel() = _uiState.update { it.copy(isPanelOpen = false) }

    fun acknowledge(id: String) = transition(id) { repository.acknowledge(id) }

    fun markDone(id: String) = transition(id) { repository.markDone(id) }

    fun cancelAndMute(id: String) = transition(id) { repository.cancelAndMute(id) }

    private fun transition(id: String, call: suspend () -> Result<ServiceRequest>) {
        if (_uiState.value.busyRequestId != null) return
        viewModelScope.launch {
            _uiState.update { it.copy(busyRequestId = id) }
            call()
                .onSuccess { updated ->
                    _uiState.update { state ->
                        val list = if (updated.status == "open" || updated.isAcknowledged) {
                            state.requests.map { if (it.id == id) updated else it }
                        } else {
                            state.requests.filterNot { it.id == id }
                        }
                        state.copy(requests = list, busyRequestId = null)
                    }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(busyRequestId = null) }
                    _errors.send(err.toUiText(R.string.service_msg_update_failed))
                    // Another device may have handled it first; show the server's current queue.
                    refresh()
                }
        }
    }
}
