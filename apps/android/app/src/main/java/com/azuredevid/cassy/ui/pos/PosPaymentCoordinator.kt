package com.azuredevid.cassy.ui.pos

import com.azuredevid.cassy.R
import com.azuredevid.cassy.core.audio.CassyAudioCue
import com.azuredevid.cassy.core.audio.CassySoundPlayer
import com.azuredevid.cassy.core.hardware.BluetoothPrinterClient
import com.azuredevid.cassy.core.hardware.PrinterPort9100Manager
import com.azuredevid.cassy.core.hardware.UnifiedEscPosPrinter
import com.azuredevid.cassy.data.model.OrderQuote
import com.azuredevid.cassy.domain.model.CreatePosOrderInput
import com.azuredevid.cassy.domain.model.CreatePosOrderItemInput
import com.azuredevid.cassy.domain.model.InitiatePosPaymentInput
import com.azuredevid.cassy.domain.model.PosOrder
import com.azuredevid.cassy.domain.repository.PosOrderRepository
import com.azuredevid.cassy.domain.repository.PrinterConfig
import com.azuredevid.cassy.domain.repository.PrinterSettingsRepository
import com.azuredevid.cassy.ui.designsystem.UiText
import com.azuredevid.cassy.ui.designsystem.toUiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

/**
 * Coordinates order creation, cash and dynamic QRIS payment initiation, polling checks, and split share advancement.
 */
internal class PosPaymentCoordinator(
    private val posOrderRepository: PosOrderRepository,
    private val printerSettings: PrinterSettingsRepository,
    private val printerManager: PrinterPort9100Manager,
    private val printDispatcher: PosPrintDispatcher,
    private val tabCoordinator: PosTabCoordinator,
    private val soundPlayer: CassySoundPlayer?,
    private val bluetoothPrinter: BluetoothPrinterClient? = null
) {
    private val unifiedPrinter = UnifiedEscPosPrinter(printerManager, bluetoothPrinter)

    suspend fun ensureOrderCreated(
        stateFlow: MutableStateFlow<PosUiState>,
        snapshot: PosUiState,
        quote: OrderQuote
    ): Result<PosOrder> {
        val existing = snapshot.pendingOrder
        if (existing != null) {
            return Result.success(existing)
        }
        if (snapshot.tableId != null) {
            return tabCoordinator.closeTab(stateFlow, snapshot) { repo, tabId -> repo.closeTab(tabId) }
        }
        val key = snapshot.pendingOrderIdempotencyKey ?: UUID.randomUUID().toString()
        val orderTypeApi = if (snapshot.orderType == "DINE-IN") "dine_in" else "takeaway"
        val customer = snapshot.customerName.trim().ifBlank {
            if (orderTypeApi == "dine_in" && snapshot.tableName.isNotBlank()) {
                "Tamu ${snapshot.tableName}"
            } else {
                "Pelanggan Walk-In"
            }
        }
        val result = posOrderRepository.createOrder(
            CreatePosOrderInput(
                idempotencyKey = key,
                tableNumber = if (orderTypeApi == "dine_in") snapshot.tableName.ifBlank { null } else null,
                customerName = customer,
                customerPhone = snapshot.matchedCustomerStamps?.phone?.ifBlank { null },
                orderType = orderTypeApi,
                expectedTotal = quote.totalAmount,
                expectedQuoteFingerprint = quote.quoteFingerprint.ifBlank { null },
                items = snapshot.cart.map {
                    CreatePosOrderItemInput(
                        productId = it.product.id,
                        quantity = it.quantity,
                        notes = it.notes
                    )
                }
            )
        )
        result.onSuccess { created ->
            stateFlow.update { it.copy(pendingOrder = created, pendingOrderIdempotencyKey = key) }
        }
        return result
    }

    suspend fun submitCashPayment(
        stateFlow: MutableStateFlow<PosUiState>,
        snapshot: PosUiState,
        quote: OrderQuote,
        tenderedAmount: Long,
        drawerId: String?,
        onClearCart: (String) -> Unit,
        onEffect: suspend (PosUiEffect) -> Unit,
        // Non-null settles by EDC (card on the store's own terminal) instead of cash: no tendered amount, no change.
        edcReference: String? = null
    ) {
        val isEdc = edcReference != null
        val paymentLabel = if (isEdc) "EDC" else "TUNAI"
        val failedMessage = if (isEdc) R.string.pos_msg_edc_failed else R.string.pos_msg_cash_failed
        stateFlow.update { it.copy(isSubmittingOrder = true, settlementError = null) }
        val order = ensureOrderCreated(stateFlow, snapshot, quote).getOrElse { err ->
            stateFlow.update {
                it.copy(
                    isSubmittingOrder = false,
                    settlementError = err.toUiText(R.string.pos_msg_order_send_failed)
                )
            }
            onEffect(PosUiEffect.ShowToast(err.toUiText(R.string.pos_msg_order_send_failed)))
            return
        }

        posOrderRepository.initiatePayment(
            InitiatePosPaymentInput(
                orderId = order.id,
                idempotencyKey = UUID.randomUUID().toString(),
                method = if (isEdc) "edc" else "cash",
                cashTenderedAmount = tenderedAmount.takeUnless { isEdc },
                drawerId = drawerId,
                splitId = stateFlow.value.pendingSplitId,
                reconciliationRef = edcReference
            )
        ).onSuccess { res ->
            val settledOrder = res.order ?: order
            val config = printerSettings.getConfig()
            val cashierEndpoint = config.cashierEndpoint()
            if (res.openDrawer && cashierEndpoint != null) {
                unifiedPrinter.openCashDrawer(cashierEndpoint)
            }
            if (!settlesWholeBill(stateFlow.value, settledOrder)) {
                advanceToNextSplit(
                    stateFlow = stateFlow,
                    paymentLabel = paymentLabel,
                    cashTenderedIdr = if (isEdc) null else res.payment.cashTenderedAmount ?: tenderedAmount,
                    changeIdr = res.payment.changeGivenAmount,
                    onEffect = onEffect
                )
                return@onSuccess
            }
            printLastShareReceipt(
                snapshot = stateFlow.value,
                paymentLabel = paymentLabel,
                cashTenderedIdr = if (isEdc) null else res.payment.cashTenderedAmount ?: tenderedAmount,
                changeIdr = res.payment.changeGivenAmount,
                config = config
            )
            val hasFailover = printDispatcher.dispatchReceiptsAndKitchenTickets(
                state = snapshot,
                order = settledOrder,
                cartSnapshot = snapshot.cart,
                tableLabel = printDispatcher.resolveTableLabel(snapshot),
                paymentLabel = paymentLabel,
                cashTenderedIdr = if (isEdc) null else res.payment.cashTenderedAmount ?: tenderedAmount,
                changeIdr = if (isEdc) null else res.payment.changeGivenAmount ?: (tenderedAmount - settledOrder.totalAmount).coerceAtLeast(0L),
                roundingIdr = res.payment.roundingAmount ?: 0L,
                config = config,
                roundNumber = snapshot.dineInRound
            )
            if (hasFailover) {
                stateFlow.update { it.copy(failoverAlert = UiText.res(R.string.pos_msg_station_failover)) }
            }
            onClearCart(settledOrder.id)
            val orderNo = settledOrder.orderNumber.ifBlank { order.id.takeLast(8).uppercase() }
            soundPlayer?.play(CassyAudioCue.ORDER_PAID)
            onEffect(PosUiEffect.CashPaymentCompleted(orderNo))
            onEffect(PosUiEffect.ShowToast(UiText.res(R.string.pos_msg_cash_done, orderNo)))
        }.onFailure { err ->
            soundPlayer?.play(CassyAudioCue.SOFT_ERROR)
            stateFlow.update {
                it.copy(
                    isSubmittingOrder = false,
                    settlementError = err.toUiText(failedMessage)
                )
            }
            onEffect(PosUiEffect.ShowToast(err.toUiText(failedMessage)))
        }
    }

    suspend fun startQrisPayment(
        stateFlow: MutableStateFlow<PosUiState>,
        snapshot: PosUiState,
        quote: OrderQuote,
        drawerId: String?,
        onStartPolling: () -> Unit,
        onCancelPolling: () -> Unit,
        onClearCart: (String) -> Unit,
        onEffect: suspend (PosUiEffect) -> Unit
    ) {
        stateFlow.update { it.copy(isSubmittingOrder = true, settlementError = null) }
        val order = ensureOrderCreated(stateFlow, snapshot, quote).getOrElse { err ->
            stateFlow.update {
                it.copy(
                    isSubmittingOrder = false,
                    settlementError = err.toUiText(R.string.pos_msg_qris_order_failed)
                )
            }
            onEffect(PosUiEffect.ShowToast(err.toUiText(R.string.pos_msg_qris_order_failed)))
            return
        }

        posOrderRepository.initiatePayment(
            InitiatePosPaymentInput(
                orderId = order.id,
                idempotencyKey = UUID.randomUUID().toString(),
                method = "qris",
                drawerId = drawerId,
                splitId = stateFlow.value.pendingSplitId
            )
        ).onSuccess { res ->
            val updatedOrder = res.order ?: order
            stateFlow.update {
                it.copy(
                    isSubmittingOrder = false,
                    pendingOrder = updatedOrder,
                    activeQrisPayment = res.payment
                )
            }
            if (updatedOrder.paymentStatus.equals("paid", ignoreCase = true) ||
                res.payment.status.equals("settled", ignoreCase = true)
            ) {
                finalizeQrisSettlement(
                    stateFlow = stateFlow,
                    order = updatedOrder,
                    onCancelPolling = onCancelPolling,
                    onClearCart = onClearCart,
                    onEffect = onEffect
                )
            } else {
                onStartPolling()
            }
        }.onFailure { err ->
            stateFlow.update {
                it.copy(
                    isSubmittingOrder = false,
                    settlementError = err.toUiText(R.string.pos_msg_qris_bill_failed)
                )
            }
            onEffect(PosUiEffect.ShowToast(err.toUiText(R.string.pos_msg_qris_bill_failed)))
        }
    }

    suspend fun checkQrisStatusOnce(
        stateFlow: MutableStateFlow<PosUiState>,
        onCancelPolling: () -> Unit,
        onClearCart: (String) -> Unit,
        onEffect: suspend (PosUiEffect) -> Unit
    ) {
        val order = stateFlow.value.pendingOrder ?: return
        if (stateFlow.value.activeQrisPayment == null) return
        stateFlow.update { it.copy(isCheckingQrisStatus = true) }
        posOrderRepository.getOrder(order.id)
            .onSuccess { detail ->
                stateFlow.update { it.copy(isCheckingQrisStatus = false, pendingOrder = detail.order) }
                val attemptId = stateFlow.value.activeQrisPayment?.id
                val paid = detail.order.paymentStatus.equals("paid", ignoreCase = true) ||
                    detail.payments.any {
                        it.id == attemptId &&
                            (it.status.equals("settled", ignoreCase = true) || it.status.equals("paid", ignoreCase = true))
                    }
                if (paid) {
                    finalizeQrisSettlement(
                        stateFlow = stateFlow,
                        order = detail.order,
                        onCancelPolling = onCancelPolling,
                        onClearCart = onClearCart,
                        onEffect = onEffect
                    )
                }
            }
            .onFailure {
                stateFlow.update { it.copy(isCheckingQrisStatus = false) }
            }
    }

    private suspend fun finalizeQrisSettlement(
        stateFlow: MutableStateFlow<PosUiState>,
        order: PosOrder,
        onCancelPolling: () -> Unit,
        onClearCart: (String) -> Unit,
        onEffect: suspend (PosUiEffect) -> Unit
    ) {
        if (!settlesWholeBill(stateFlow.value, order)) {
            onCancelPolling()
            advanceToNextSplit(
                stateFlow = stateFlow,
                paymentLabel = "QRIS",
                onEffect = onEffect
            )
            return
        }
        val snapshot = stateFlow.value
        val config = printerSettings.getConfig()
        printLastShareReceipt(snapshot, paymentLabel = "QRIS", cashTenderedIdr = null, changeIdr = null, config = config)
        val hasFailover = printDispatcher.dispatchReceiptsAndKitchenTickets(
            state = snapshot,
            order = order,
            cartSnapshot = snapshot.cart,
            tableLabel = printDispatcher.resolveTableLabel(snapshot),
            paymentLabel = "QRIS DINAMIS",
            cashTenderedIdr = null,
            changeIdr = null,
            roundingIdr = 0L,
            config = config,
            roundNumber = snapshot.dineInRound
        )
        if (hasFailover) {
            stateFlow.update { it.copy(failoverAlert = UiText.res(R.string.pos_msg_station_failover)) }
        }
        onClearCart(order.id)
        val orderNo = order.orderNumber.ifBlank { order.id.takeLast(8).uppercase() }
        soundPlayer?.play(CassyAudioCue.ORDER_PAID)
        onEffect(PosUiEffect.QrisPaymentCompleted(orderNo))
        onEffect(PosUiEffect.ShowToast(UiText.res(R.string.pos_msg_qris_paid, orderNo)))
    }

    /**
     * The share that settles a split bill gets its own receipt too (OPS-04, S5-Q1), then the full receipt.
     * No-op when the bill was not split.
     */
    private suspend fun printLastShareReceipt(
        snapshot: PosUiState,
        paymentLabel: String,
        cashTenderedIdr: Long?,
        changeIdr: Long?,
        config: PrinterConfig
    ) {
        val share = snapshot.selectedSplit ?: return
        printDispatcher.printShareReceipt(snapshot, share, paymentLabel, cashTenderedIdr, changeIdr, config)
    }

    private fun settlesWholeBill(state: PosUiState, order: PosOrder): Boolean {
        return order.paymentStatus.equals("paid", ignoreCase = true) ||
            state.tabSplits.none { !it.isPaid && it.id != state.pendingSplitId }
    }

    private suspend fun advanceToNextSplit(
        stateFlow: MutableStateFlow<PosUiState>,
        paymentLabel: String,
        cashTenderedIdr: Long? = null,
        changeIdr: Long? = null,
        onEffect: suspend (PosUiEffect) -> Unit
    ) {
        val state = stateFlow.value
        val paid = state.selectedSplit ?: return
        printDispatcher.printShareReceipt(
            state = state,
            share = paid,
            paymentLabel = paymentLabel,
            cashTenderedIdr = cashTenderedIdr,
            changeIdr = changeIdr,
            config = printerSettings.getConfig()
        )
        val splits = state.tabSplits.map {
            if (it.id == paid.id) it.copy(status = "paid", paidAmount = it.totalAmount) else it
        }
        stateFlow.update {
            it.copy(
                tabSplits = splits,
                pendingSplitId = splits.firstOrNull { split -> !split.isPaid }?.id,
                showCashModal = false,
                showEdcModal = false,
                isSubmittingOrder = false,
                activeQrisPayment = null,
                isCheckingQrisStatus = false,
                settlementError = null
            )
        }
        soundPlayer?.play(CassyAudioCue.ORDER_PAID)
        onEffect(PosUiEffect.ShowToast(UiText.res(R.string.pos_msg_split_paid, paid.number)))
    }
}
