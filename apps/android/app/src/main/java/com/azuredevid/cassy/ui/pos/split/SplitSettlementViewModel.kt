package com.azuredevid.cassy.ui.pos.split

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.azuredevid.cassy.R
import com.azuredevid.cassy.domain.error.DomainError
import com.azuredevid.cassy.domain.error.DomainException
import com.azuredevid.cassy.domain.model.InitiatePosPaymentInput
import com.azuredevid.cassy.domain.model.OpenSplitBillSummary
import com.azuredevid.cassy.domain.model.PosOrder
import com.azuredevid.cassy.domain.model.PosPaymentAttempt
import com.azuredevid.cassy.domain.model.SplitBill
import com.azuredevid.cassy.domain.model.SplitPlan
import com.azuredevid.cassy.domain.repository.PosOrderRepository
import com.azuredevid.cassy.domain.repository.SplitBillRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/** Inline message: a string resource, or text the backend already wrote for the user. */
sealed interface SplitMessage {
    data class Res(@StringRes val id: Int) : SplitMessage
    data class Server(val text: String) : SplitMessage
}

enum class SplitStage {
    CLOSED,
    LOADING,
    /** Undivided order: the cashier picks equal or by-item shares. */
    CHOOSE_PLAN,
    /** Divided order: shares are collected one by one. */
    SETTLE,
    /** Divided bills with money still owed, to resume or to collect a guest's unpaid share. */
    OPEN_BILLS
}

/** Split settlement dialog state. Every amount shown comes from the server. */
@Immutable
data class SplitSettlementUiState(
    val stage: SplitStage = SplitStage.CLOSED,
    val bill: SplitBill? = null,
    val openBills: List<OpenSplitBillSummary> = emptyList(),
    val isBusy: Boolean = false,
    val payingShareId: String? = null,
    val cashShareId: String? = null,
    val activeQris: PosPaymentAttempt? = null,
    val qrisShareId: String? = null,
    val lastChangeGiven: Long? = null,
    val message: SplitMessage? = null
)

sealed interface SplitSettlementEffect {
    /** A cash share was taken; kick the drawer if a cashier printer is set. */
    data object OpenDrawer : SplitSettlementEffect

    /** The server reports the whole order paid: print receipts, clear the cart, close the dialog. */
    data class BillSettled(val order: PosOrder) : SplitSettlementEffect
}

/**
 * Cashier bill splitting and share-by-share settlement for a pay-first order (Release One PR 9).
 *
 * - Shares and every amount come from the server (`POST /api/v1/pos/orders/{id}/splits`); the client
 *   never prices a share or sums items.
 * - A share is paid with `split_id`. Cash collects the remainder of that share; dynamic QRIS shows a
 *   per-share code. Each share keeps its idempotency key until the server answers definitively, so a
 *   retry after a dropped connection cannot charge twice.
 * - After every payment the bill is reloaded; [SplitSettlementEffect.BillSettled] fires only when the
 *   server's `payment_status` is `paid`. The kitchen gets the order at that moment because the backend
 *   creates KDS tickets on the final settlement only.
 */
class SplitSettlementViewModel(
    private val splitBills: SplitBillRepository,
    private val posOrders: PosOrderRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SplitSettlementUiState())
    val uiState: StateFlow<SplitSettlementUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<SplitSettlementEffect>(extraBufferCapacity = 4)
    val effects: SharedFlow<SplitSettlementEffect> = _effects.asSharedFlow()

    /** Pending idempotency key per share, kept across retries until the server answers. */
    private val shareKeys = mutableMapOf<String, String>()

    /** Opens the dialog for [orderId]: plan choice when undivided, settlement when already divided. */
    fun open(orderId: String) {
        _uiState.update { SplitSettlementUiState(stage = SplitStage.LOADING, isBusy = true) }
        viewModelScope.launch { reload(orderId, initial = true) }
    }

    fun showOpenBills(query: String = "") {
        _uiState.update { it.copy(stage = SplitStage.OPEN_BILLS, isBusy = true, message = null) }
        viewModelScope.launch {
            splitBills.listOpenBills(query).fold(
                onSuccess = { bills -> _uiState.update { it.copy(openBills = bills, isBusy = false) } },
                onFailure = { err -> _uiState.update { it.copy(isBusy = false, message = err.toMessage(R.string.split_error_load)) } }
            )
        }
    }

    fun close() {
        _uiState.value = SplitSettlementUiState()
    }

    /** Divides the open bill. Equal needs 2 to 20 shares; by item needs at least two non-empty shares. */
    fun divide(plan: SplitPlan) {
        val bill = _uiState.value.bill ?: return
        if (_uiState.value.isBusy) return
        val valid = when (plan) {
            is SplitPlan.Equal -> plan.count in MIN_SHARES..MAX_SHARES
            is SplitPlan.ByItem -> plan.shares.size in MIN_SHARES..MAX_SHARES && plan.shares.all { s -> s.items.any { it.quantity > 0 } }
        }
        if (!valid) {
            _uiState.update { it.copy(message = SplitMessage.Res(R.string.split_error_plan)) }
            return
        }
        _uiState.update { it.copy(isBusy = true, message = null) }
        viewModelScope.launch {
            splitBills.divide(bill.order.id, plan).fold(
                onSuccess = { divided -> _uiState.update { it.copy(isBusy = false, bill = divided, stage = SplitStage.SETTLE) } },
                onFailure = { err ->
                    _uiState.update { it.copy(isBusy = false, message = err.toMessage(R.string.split_error_divide)) }
                    if (err.conflictCode() == SplitBillRepository.CODE_SPLIT_NOT_ALLOWED) reload(bill.order.id, initial = true)
                }
            )
        }
    }

    fun startCashForShare(shareId: String) {
        val share = _uiState.value.bill?.shares?.firstOrNull { it.id == shareId } ?: return
        if (!share.isCollectable) return
        _uiState.update { it.copy(cashShareId = shareId, message = null) }
    }

    fun cancelCash() {
        _uiState.update { it.copy(cashShareId = null) }
    }

    /** Takes cash for one share; the server validates the tendered amount and returns the change. */
    fun payShareCash(shareId: String, tendered: Long, drawerId: String?) {
        pay(shareId) { key, orderId ->
            InitiatePosPaymentInput(
                orderId = orderId, idempotencyKey = key, method = METHOD_CASH,
                cashTenderedAmount = tendered, drawerId = drawerId, splitId = shareId
            )
        }
    }

    /** Creates a dynamic QRIS for one share; the guest scans it from the tablet. */
    fun payShareQris(shareId: String, drawerId: String?) {
        pay(shareId) { key, orderId ->
            InitiatePosPaymentInput(orderId = orderId, idempotencyKey = key, method = METHOD_QRIS, drawerId = drawerId, splitId = shareId)
        }
    }

    /** Re-reads the bill to see whether the active QRIS share has settled. */
    fun checkQrisStatus() {
        val orderId = _uiState.value.bill?.order?.id ?: return
        _uiState.update { it.copy(isBusy = true) }
        viewModelScope.launch { reload(orderId, initial = false) }
    }

    fun dismissQris() {
        _uiState.update { it.copy(activeQris = null, qrisShareId = null) }
    }

    private fun pay(shareId: String, input: (key: String, orderId: String) -> InitiatePosPaymentInput) {
        val state = _uiState.value
        val bill = state.bill ?: return
        val share = bill.shares.firstOrNull { it.id == shareId } ?: return
        if (state.isBusy || !share.isCollectable) return
        val key = shareKeys.getOrPut(shareId) { UUID.randomUUID().toString() }
        _uiState.update { it.copy(isBusy = true, payingShareId = shareId, message = null) }
        viewModelScope.launch {
            posOrders.initiatePayment(input(key, bill.order.id)).fold(
                onSuccess = { result ->
                    shareKeys.remove(shareId)
                    if (result.openDrawer) _effects.tryEmit(SplitSettlementEffect.OpenDrawer)
                    val isQris = result.payment.method == METHOD_QRIS && !result.payment.status.equals("paid", ignoreCase = true)
                    _uiState.update {
                        it.copy(
                            cashShareId = null,
                            payingShareId = null,
                            lastChangeGiven = result.payment.changeGivenAmount ?: it.lastChangeGiven,
                            activeQris = if (isQris) result.payment else null,
                            qrisShareId = if (isQris) shareId else null
                        )
                    }
                    reload(bill.order.id, initial = false)
                },
                onFailure = { err ->
                    // Keep the key only when the outcome is unknown (network), so a retry is deduplicated.
                    if ((err as? DomainException)?.error !is DomainError.NetworkUnavailable) shareKeys.remove(shareId)
                    _uiState.update { it.copy(isBusy = false, payingShareId = null, message = err.toMessage(R.string.split_error_pay)) }
                    if (err.conflictCode() != null) reload(bill.order.id, initial = false)
                }
            )
        }
    }

    private suspend fun reload(orderId: String, initial: Boolean) {
        splitBills.getBill(orderId).fold(
            onSuccess = { bill ->
                val undivided = bill.shares.size <= 1 && bill.order.paidAmount == 0L && !bill.isFullyPaid
                val qrisSettled = _uiState.value.qrisShareId?.let { id -> bill.shares.firstOrNull { it.id == id }?.isSettled } == true
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        bill = bill,
                        stage = if (initial && undivided) SplitStage.CHOOSE_PLAN else SplitStage.SETTLE,
                        activeQris = if (qrisSettled) null else it.activeQris,
                        qrisShareId = if (qrisSettled) null else it.qrisShareId
                    )
                }
                if (bill.isFullyPaid) _effects.tryEmit(SplitSettlementEffect.BillSettled(bill.order))
            },
            onFailure = { err ->
                _uiState.update {
                    it.copy(isBusy = false, stage = if (it.stage == SplitStage.LOADING) SplitStage.SETTLE else it.stage, message = err.toMessage(R.string.split_error_load))
                }
            }
        )
    }

    private fun Throwable.conflictCode(): String? = ((this as? DomainException)?.error as? DomainError.Conflict)?.code

    private fun Throwable.toMessage(@StringRes fallback: Int): SplitMessage =
        (this as? DomainException)?.error?.message?.takeIf { it.isNotBlank() }?.let { SplitMessage.Server(it) }
            ?: SplitMessage.Res(fallback)

    private companion object {
        const val METHOD_CASH = "cash"
        const val METHOD_QRIS = "qris"
        const val MIN_SHARES = 2
        const val MAX_SHARES = 20
    }
}
