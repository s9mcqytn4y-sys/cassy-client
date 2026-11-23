package com.azuredevid.cassy.ui.pos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.azuredevid.cassy.R
import com.azuredevid.cassy.ui.designsystem.CassyDimens
import com.azuredevid.cassy.ui.designsystem.CassyModalDialog
import com.azuredevid.cassy.ui.designsystem.CassyPrimaryButton
import com.azuredevid.cassy.ui.designsystem.CassySecondaryButton
import com.azuredevid.cassy.ui.designsystem.CassyTextField
import com.azuredevid.cassy.ui.pos.components.PosCashModal
import com.azuredevid.cassy.ui.pos.components.PosEdcModal
import com.azuredevid.cassy.ui.pos.components.PosItemSplitDialog
import com.azuredevid.cassy.ui.pos.components.PosQrisModal
import com.azuredevid.cassy.ui.pos.components.PosServiceRequestPill
import com.azuredevid.cassy.ui.pos.components.PosServiceRequestsModal
import com.azuredevid.cassy.ui.pos.components.PosTabItemVoidDialog
import com.azuredevid.cassy.ui.pos.split.SplitSettlementUiState
import com.azuredevid.cassy.ui.pos.split.SplitSettlementViewModel
import com.azuredevid.cassy.ui.pos.split.components.SplitSettlementDialog
import com.azuredevid.cassy.ui.shift.ShiftUiState
import com.azuredevid.cassy.ui.shift.ShiftViewModel
import com.azuredevid.cassy.ui.shift.components.CashMovementModal
import com.azuredevid.cassy.ui.shift.components.CloseShiftModal
import com.azuredevid.cassy.ui.shift.components.OpenShiftModal
import com.azuredevid.cassy.ui.shift.components.XReportModal
import java.text.NumberFormat

@Composable
internal fun BoxScope.PosLandscapeModals(
    posState: PosUiState,
    shiftState: ShiftUiState,
    serviceState: ServiceRequestUiState,
    voidState: TabItemVoidUiState,
    splitState: SplitSettlementUiState,
    posViewModel: PosViewModel,
    shiftViewModel: ShiftViewModel,
    serviceRequestViewModel: ServiceRequestViewModel,
    tabItemVoidViewModel: TabItemVoidViewModel,
    splitViewModel: SplitSettlementViewModel,
    rupiahFormat: NumberFormat,
    showDigitalReceiptModal: Boolean,
    onDismissDigitalReceiptModal: () -> Unit
) {
    posState.itemSplit?.let { draft ->
        PosItemSplitDialog(
            draft = draft,
            isBusy = posState.isSubmittingOrder,
            onGuestsChange = posViewModel::setItemSplitGuests,
            onAssign = posViewModel::assignUnit,
            onConfirm = posViewModel::confirmItemSplit,
            onDismiss = posViewModel::dismissItemSplit
        )
    }

    val pricedQuote = posState.quote
    if (posState.showCashModal && pricedQuote != null) {
        PosCashModal(
            grandTotal = posState.payableAmount ?: pricedQuote.totalAmount,
            rupiahFormat = rupiahFormat,
            isSubmitting = posState.isSubmittingOrder,
            errorMessage = posState.settlementError?.asString(),
            onDismiss = { posViewModel.closeCashModal() },
            onConfirmCashPayment = { tenderedLong, _ ->
                posViewModel.submitCashPayment(
                    tenderedAmount = tenderedLong,
                    drawerId = shiftState.currentShift?.drawerId
                )
            }
        )
    }

    if (posState.showEdcModal && pricedQuote != null) {
        PosEdcModal(
            grandTotal = posState.payableAmount ?: pricedQuote.totalAmount,
            rupiahFormat = rupiahFormat,
            isSubmitting = posState.isSubmittingOrder,
            errorMessage = posState.settlementError?.asString(),
            onDismiss = { posViewModel.closeEdcModal() },
            onConfirmEdcPayment = { reference ->
                posViewModel.submitEdcPayment(
                    reference = reference,
                    drawerId = shiftState.currentShift?.drawerId
                )
            }
        )
    }

    val activeQris = posState.activeQrisPayment
    if (activeQris != null) {
        PosQrisModal(
            orderNumber = posState.pendingOrder?.orderNumber.orEmpty(),
            payment = activeQris,
            rupiahFormat = rupiahFormat,
            isCheckingStatus = posState.isCheckingQrisStatus,
            onCheckStatus = { posViewModel.refreshQrisStatus() },
            onDismiss = { posViewModel.dismissQrisModal() }
        )
    }

    if (serviceState.requests.isNotEmpty()) {
        PosServiceRequestPill(
            count = serviceState.requests.size,
            onClick = { serviceRequestViewModel.openPanel() },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = CassyDimens.SpaceSm)
        )
    }

    if (voidState.isOpen) {
        PosTabItemVoidDialog(
            state = voidState,
            onQuantityChange = tabItemVoidViewModel::setQuantity,
            onReasonChange = tabItemVoidViewModel::setReason,
            onPinChange = tabItemVoidViewModel::setPin,
            onOwnerPinChange = tabItemVoidViewModel::setOwnerPin,
            onSubmit = tabItemVoidViewModel::submit,
            onAskOwner = tabItemVoidViewModel::requestOwnerApproval,
            onDismiss = tabItemVoidViewModel::dismiss
        )
    }

    if (serviceState.isPanelOpen) {
        PosServiceRequestsModal(
            requests = serviceState.requests,
            busyRequestId = serviceState.busyRequestId,
            onAcknowledge = { serviceRequestViewModel.acknowledge(it) },
            onDone = { serviceRequestViewModel.markDone(it) },
            onCancelMute = { serviceRequestViewModel.cancelAndMute(it) },
            onDismiss = { serviceRequestViewModel.closePanel() }
        )
    }

    SplitSettlementDialog(
        state = splitState,
        rupiahFormat = rupiahFormat,
        onDivide = { plan -> splitViewModel.divide(plan) },
        onPayCash = { shareId -> splitViewModel.startCashForShare(shareId) },
        onPayQris = { shareId -> splitViewModel.payShareQris(shareId, shiftState.currentShift?.drawerId) },
        onOpenBill = { orderId -> splitViewModel.open(orderId) },
        onDismiss = {
            splitViewModel.close()
            posViewModel.closeSplitBill()
        }
    )

    val cashShare = splitState.cashShareId?.let { id -> splitState.bill?.shares?.firstOrNull { it.id == id } }
    if (cashShare != null) {
        PosCashModal(
            grandTotal = cashShare.remainingAmount,
            rupiahFormat = rupiahFormat,
            isSubmitting = splitState.payingShareId == cashShare.id,
            onDismiss = { splitViewModel.cancelCash() },
            onConfirmCashPayment = { tenderedLong, _ ->
                splitViewModel.payShareCash(cashShare.id, tenderedLong, shiftState.currentShift?.drawerId)
            }
        )
    }

    val splitQris = splitState.activeQris
    if (splitQris != null) {
        PosQrisModal(
            orderNumber = splitState.bill?.order?.orderNumber.orEmpty(),
            payment = splitQris,
            rupiahFormat = rupiahFormat,
            isCheckingStatus = splitState.isBusy,
            onCheckStatus = { splitViewModel.checkQrisStatus() },
            onDismiss = { splitViewModel.dismissQris() }
        )
    }

    if (shiftState.showOpenShiftModal) {
        OpenShiftModal(
            drawers = shiftState.drawers,
            selectedDrawerId = shiftState.selectedDrawerId,
            onDrawerSelected = { shiftViewModel.onDrawerSelected(it) },
            openingFloatInput = shiftState.openingFloatInput,
            notes = shiftState.openShiftNotes,
            isOpening = shiftState.isOpeningShift,
            errorMessage = shiftState.errorMessage?.asString(),
            onOpeningFloatChanged = { shiftViewModel.onOpeningFloatChanged(it) },
            onNotesChanged = { shiftViewModel.onOpenShiftNotesChanged(it) },
            onSubmit = { shiftViewModel.submitOpenShift() },
            onDismiss = { shiftViewModel.closeOpenShiftModal() }
        )
    }

    if (shiftState.showCashMovementModal) {
        CashMovementModal(
            movementType = shiftState.movementType,
            amountInput = shiftState.movementAmountInput,
            reason = shiftState.movementReason,
            isRecording = shiftState.isRecordingMovement,
            errorMessage = shiftState.errorMessage?.asString(),
            onMovementTypeChanged = { shiftViewModel.onMovementTypeChanged(it) },
            onAmountChanged = { shiftViewModel.onMovementAmountChanged(it) },
            onReasonChanged = { shiftViewModel.onMovementReasonChanged(it) },
            onSubmit = { shiftViewModel.submitCashMovement() },
            onDismiss = { shiftViewModel.closeCashMovementModal() }
        )
    }

    if (shiftState.showXReportModal) {
        XReportModal(
            report = shiftState.currentXReport,
            isLoading = shiftState.isLoadingXReport,
            isPrinting = shiftState.isPrintingReport,
            onPrint = { shiftViewModel.printCurrentXReport() },
            onDismiss = { shiftViewModel.closeXReportModal() }
        )
    }

    if (shiftState.showCloseShiftModal) {
        CloseShiftModal(
            currentShift = shiftState.currentShift,
            countedCashInput = shiftState.countedCashInput,
            varianceReason = shiftState.varianceReason,
            notes = shiftState.closeShiftNotes,
            supervisorPinInput = shiftState.supervisorPinInput,
            calculatedVariance = shiftState.calculatedVariance,
            isSupervisorAuthRequired = shiftState.isSupervisorAuthRequired,
            printReceiptOnClose = shiftState.printReceiptOnClose,
            isClosing = shiftState.isClosingShift,
            errorMessage = shiftState.errorMessage?.asString(),
            onCountedCashChanged = { shiftViewModel.onCountedCashChanged(it) },
            onVarianceReasonChanged = { shiftViewModel.onVarianceReasonChanged(it) },
            onNotesChanged = { shiftViewModel.onCloseShiftNotesChanged(it) },
            onSupervisorPinChanged = { shiftViewModel.onSupervisorPinChanged(it) },
            onPrintReceiptToggled = { shiftViewModel.onPrintReceiptOnCloseToggled(it) },
            onSubmit = { shiftViewModel.submitCloseShift() },
            onDismiss = { shiftViewModel.closeCloseShiftModal() }
        )
    }

    val lastOrderId = posState.lastSettledOrderId
    if (showDigitalReceiptModal && !lastOrderId.isNullOrBlank()) {
        PosDigitalReceiptDialog(
            orderId = lastOrderId,
            onSend = { channel, recipient ->
                posViewModel.sendDigitalReceipt(lastOrderId, channel, recipient)
                onDismissDigitalReceiptModal()
            },
            onDismiss = onDismissDigitalReceiptModal
        )
    }
}

@Composable
private fun PosDigitalReceiptDialog(
    orderId: String,
    onSend: (channel: String, recipient: String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedChannel by rememberSaveable(orderId) { mutableStateOf("whatsapp") }
    var recipient by rememberSaveable(orderId) { mutableStateOf("") }

    CassyModalDialog(
        title = stringResource(R.string.pos_digital_receipt_title),
        subtitle = stringResource(R.string.pos_digital_receipt_subtitle),
        icon = Icons.AutoMirrored.Rounded.Send,
        maxWidth = CassyDimens.ModalWidthStandard,
        onDismissRequest = onDismiss,
        footer = {
            CassySecondaryButton(
                text = stringResource(R.string.btn_cancel),
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            )
            CassyPrimaryButton(
                text = stringResource(R.string.pos_digital_receipt_btn_send),
                enabled = recipient.trim().isNotEmpty(),
                onClick = { onSend(selectedChannel, recipient.trim()) },
                modifier = Modifier.weight(1f)
            )
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceSm)
        ) {
            FilterChip(
                selected = selectedChannel == "whatsapp",
                onClick = { selectedChannel = "whatsapp" },
                label = { Text(stringResource(R.string.pos_digital_receipt_channel_wa)) },
                modifier = Modifier.defaultMinSize(minHeight = CassyDimens.TouchTargetMin)
            )
            FilterChip(
                selected = selectedChannel == "email",
                onClick = { selectedChannel = "email" },
                label = { Text(stringResource(R.string.pos_digital_receipt_channel_email)) },
                modifier = Modifier.defaultMinSize(minHeight = CassyDimens.TouchTargetMin)
            )
        }

        val isWhatsapp = selectedChannel == "whatsapp"
        CassyTextField(
            value = recipient,
            onValueChange = { recipient = it },
            label = stringResource(
                if (isWhatsapp) R.string.pos_digital_receipt_input_wa_label
                else R.string.pos_digital_receipt_input_email_label
            ),
            placeholder = stringResource(
                if (isWhatsapp) R.string.pos_digital_receipt_input_wa_hint
                else R.string.pos_digital_receipt_input_email_hint
            )
        )
    }
}
