package com.azuredevid.cassy.ui.pos

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.azuredevid.cassy.R
import com.azuredevid.cassy.domain.error.DomainError
import com.azuredevid.cassy.domain.error.DomainException
import com.azuredevid.cassy.domain.model.PosOrder
import com.azuredevid.cassy.domain.model.PosOrderItem
import com.azuredevid.cassy.data.network.RealtimeConnectionState
import com.azuredevid.cassy.data.network.RealtimeEventEnvelope
import com.azuredevid.cassy.domain.repository.ApprovalRepository
import com.azuredevid.cassy.domain.repository.RealtimeEventSource
import com.azuredevid.cassy.domain.repository.TabItemVoidInput
import com.azuredevid.cassy.domain.repository.TableTabRepository
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
import kotlinx.coroutines.launch

/** The tab as it stands after a void, and the name to confirm it with. */
data class TabItemVoided(val tab: PosOrder, val itemName: String)

/** FORM: cashier PIN. OWNER: the value is above the cashier's limit. WAITING: the owner's phone decides. */
enum class TabVoidStep { FORM, OWNER, WAITING }

@Immutable
data class TabItemVoidUiState(
    val tabId: String? = null,
    val itemId: String = "",
    val itemName: String = "",
    val maxQuantity: Int = 0,
    val quantity: Int = 1,
    val reason: String = "",
    val pin: String = "",
    val ownerPin: String = "",
    val step: TabVoidStep = TabVoidStep.FORM,
    val isBusy: Boolean = false,
    val error: UiText? = null
) {
    val isOpen: Boolean get() = tabId != null
    val canSubmit: Boolean
        get() = !isBusy && reason.isNotBlank() && pin.length == PIN_LENGTH &&
            (step != TabVoidStep.OWNER || ownerPin.length == PIN_LENGTH)
    val canAskOwner: Boolean get() = !isBusy && reason.isNotBlank() && pin.length == PIN_LENGTH

    companion object {
        const val PIN_LENGTH = 6
    }
}

/**
 * Takes units of one sent line off an open tab. The cashier's own PIN covers values up to the store's
 * owner threshold; above it the server answers OWNER_PIN_REQUIRED and the cashier either types the
 * owner PIN here or sends a remote request (ADR-007) and waits for the owner's phone. The server binds
 * that approval to this line and quantity, so the form is frozen while waiting.
 */
class TabItemVoidViewModel(
    private val tabs: TableTabRepository,
    private val approvals: ApprovalRepository,
    private val realtime: RealtimeEventSource? = null,
    private val pollIntervalMs: Long = 3_000L
) : ViewModel() {

    private val _uiState = MutableStateFlow(TabItemVoidUiState())
    val uiState: StateFlow<TabItemVoidUiState> = _uiState.asStateFlow()

    private val _voided = Channel<TabItemVoided>(Channel.BUFFERED)
    /** The tab after a successful void; the POS cart rebuilds its sent rows from it. */
    val voided = _voided.receiveAsFlow()

    private var pollJob: Job? = null

    private companion object {
        const val OWNER_DECISION_WINDOW_MS = 125_000L
    }

    fun open(tabId: String, line: PosOrderItem, itemName: String) {
        pollJob?.cancel()
        _uiState.value = TabItemVoidUiState(tabId = tabId, itemId = line.id, itemName = itemName, maxQuantity = line.quantity)
    }

    fun dismiss() {
        // An unused approval simply expires on the server.
        pollJob?.cancel()
        _uiState.value = TabItemVoidUiState()
    }

    fun setQuantity(quantity: Int) = edit { it.copy(quantity = quantity.coerceIn(1, it.maxQuantity)) }

    fun setReason(reason: String) = edit { it.copy(reason = reason) }

    fun setPin(pin: String) = edit { it.copy(pin = pin.filter(Char::isDigit).take(TabItemVoidUiState.PIN_LENGTH)) }

    fun setOwnerPin(pin: String) = edit { it.copy(ownerPin = pin.filter(Char::isDigit).take(TabItemVoidUiState.PIN_LENGTH)) }

    fun submit() {
        val state = _uiState.value
        if (!state.canSubmit) return
        void(ownerPin = state.ownerPin.takeIf { state.step == TabVoidStep.OWNER }, approvalId = null)
    }

    fun requestOwnerApproval() {
        val state = _uiState.value
        if (!state.canAskOwner) return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, error = null) }
            approvals.requestVoidItem(state.itemId, state.quantity, state.reason.trim())
                .onSuccess { approval ->
                    _uiState.update { it.copy(isBusy = false, step = TabVoidStep.WAITING) }
                    waitForOwner(approval.id)
                }
                .onFailure { err -> _uiState.update { it.copy(isBusy = false, error = err.toUiText(R.string.tab_void_msg_request_failed)) } }
        }
    }

    private fun waitForOwner(approvalId: String) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            val socketJob = realtime?.let { source ->
                launch {
                    source.events.collect { event ->
                        val relevant = event.type == "approval.decided" ||
                            event.type == RealtimeEventEnvelope.TYPE_RESYNC_REQUIRED
                        if (!relevant) return@collect
                        val targetId = event.payloadString("approval_id") ?: event.payloadString("id")
                        if (targetId != null && targetId != approvalId) return@collect
                        if (checkApprovalDecision(approvalId)) {
                            pollJob?.cancel()
                        }
                    }
                }
            }
            try {
                // The owner has 2 minutes to decide; while the socket is connected we stay event-driven
                // and only poll as a fallback when the socket is down or absent.
                repeat((OWNER_DECISION_WINDOW_MS / pollIntervalMs).toInt() + 1) {
                    delay(pollIntervalMs)
                    val socketConnected = realtime?.connectionState?.value == RealtimeConnectionState.CONNECTED
                    if (socketConnected) return@repeat
                    if (checkApprovalDecision(approvalId)) return@launch
                }
                if (_uiState.value.step == TabVoidStep.WAITING) {
                    backToOwner(R.string.tab_void_msg_expired)
                }
            } finally {
                socketJob?.cancel()
            }
        }
    }

    private suspend fun checkApprovalDecision(approvalId: String): Boolean {
        val status = approvals.get(approvalId).getOrNull()?.status ?: return false
        return when (status) {
            "pending" -> false
            "approved" -> {
                void(ownerPin = null, approvalId = approvalId)
                true
            }
            "rejected" -> {
                backToOwner(R.string.tab_void_msg_rejected)
                true
            }
            else -> {
                backToOwner(R.string.tab_void_msg_expired)
                true
            }
        }
    }

    private fun backToOwner(message: Int) {
        _uiState.update { it.copy(step = TabVoidStep.OWNER, error = UiText.res(message)) }
    }

    private fun void(ownerPin: String?, approvalId: String?) {
        val state = _uiState.value
        val tabId = state.tabId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, error = null) }
            tabs.voidItem(tabId, state.itemId, TabItemVoidInput(state.quantity, state.reason.trim(), state.pin, ownerPin, approvalId))
                .onSuccess { order ->
                    _uiState.value = TabItemVoidUiState()
                    _voided.send(TabItemVoided(order, state.itemName))
                }
                .onFailure { err ->
                    val needsOwner = ((err as? DomainException)?.error as? DomainError.Validation)?.code == "OWNER_PIN_REQUIRED"
                    _uiState.update {
                        if (needsOwner) {
                            it.copy(isBusy = false, step = TabVoidStep.OWNER)
                        } else {
                            it.copy(
                                isBusy = false,
                                step = if (it.step == TabVoidStep.WAITING) TabVoidStep.OWNER else it.step,
                                error = err.toUiText(R.string.tab_void_msg_failed)
                            )
                        }
                    }
                }
        }
    }

    private fun edit(change: (TabItemVoidUiState) -> TabItemVoidUiState) {
        // The remote approval is bound to the quantity sent with it.
        if (_uiState.value.step == TabVoidStep.WAITING) return
        _uiState.update { change(it).copy(error = null) }
    }
}
