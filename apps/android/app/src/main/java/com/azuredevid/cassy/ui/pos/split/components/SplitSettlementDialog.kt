package com.azuredevid.cassy.ui.pos.split.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallSplit
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.azuredevid.cassy.R
import com.azuredevid.cassy.domain.model.ItemAllocation
import com.azuredevid.cassy.domain.model.OpenSplitBillSummary
import com.azuredevid.cassy.domain.model.PosOrderItem
import com.azuredevid.cassy.domain.model.ShareAllocation
import com.azuredevid.cassy.domain.model.SplitBill
import com.azuredevid.cassy.domain.model.SplitPlan
import com.azuredevid.cassy.domain.model.SplitShare
import com.azuredevid.cassy.ui.designsystem.CassyCard
import com.azuredevid.cassy.ui.designsystem.CassyDimens
import com.azuredevid.cassy.ui.designsystem.CassyEmptyState
import com.azuredevid.cassy.ui.designsystem.CassyErrorBanner
import com.azuredevid.cassy.ui.designsystem.CassyModalDialog
import com.azuredevid.cassy.ui.designsystem.CassyPrimaryButton
import com.azuredevid.cassy.ui.designsystem.CassySecondaryButton
import com.azuredevid.cassy.ui.designsystem.CassyStatusBadge
import com.azuredevid.cassy.ui.designsystem.CassyTextActionButton
import com.azuredevid.cassy.ui.designsystem.CassyToastTone
import com.azuredevid.cassy.ui.designsystem.CassyTonalButton
import com.azuredevid.cassy.ui.pos.split.SplitMessage
import com.azuredevid.cassy.ui.pos.split.SplitSettlementUiState
import com.azuredevid.cassy.ui.pos.split.SplitStage
import java.text.NumberFormat

/**
 * Cashier split-bill dialog: choose a plan, collect each share, or resume an open bill.
 * Every amount rendered here is a server field; the dialog never sums or divides money.
 */
@Composable
fun SplitSettlementDialog(
    state: SplitSettlementUiState,
    rupiahFormat: NumberFormat,
    onDivide: (SplitPlan) -> Unit,
    onPayCash: (shareId: String) -> Unit,
    onPayQris: (shareId: String) -> Unit,
    onOpenBill: (orderId: String) -> Unit,
    onDismiss: () -> Unit
) {
    if (state.stage == SplitStage.CLOSED) return
    val bill = state.bill
    val title = when (state.stage) {
        SplitStage.OPEN_BILLS -> stringResource(R.string.split_open_bills_title)
        else -> stringResource(R.string.split_title)
    }
    val subtitle = bill?.order?.orderNumber?.takeIf { it.isNotBlank() && state.stage != SplitStage.OPEN_BILLS }
        ?.let { stringResource(R.string.split_subtitle_order, it) }

    CassyModalDialog(
        title = title,
        subtitle = subtitle,
        icon = if (state.stage == SplitStage.OPEN_BILLS) Icons.AutoMirrored.Rounded.ReceiptLong else Icons.AutoMirrored.Rounded.CallSplit,
        onDismissRequest = onDismiss,
        dismissEnabled = state.payingShareId == null,
        maxWidth = CassyDimens.ModalWidthStandard,
        footer = {
            CassyTextActionButton(text = stringResource(R.string.split_btn_close), onClick = onDismiss, enabled = state.payingShareId == null)
        }
    ) {
        state.message?.let { msg ->
            CassyErrorBanner(
                message = when (msg) {
                    is SplitMessage.Res -> stringResource(msg.id)
                    is SplitMessage.Server -> msg.text
                }
            )
        }
        when (state.stage) {
            SplitStage.LOADING, SplitStage.CLOSED -> Text(
                text = stringResource(R.string.split_loading),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(vertical = CassyDimens.SpaceLg)
            )
            SplitStage.CHOOSE_PLAN -> if (bill != null) {
                SplitPlanContent(bill = bill, rupiahFormat = rupiahFormat, isBusy = state.isBusy, onDivide = onDivide)
            }
            SplitStage.SETTLE -> if (bill != null) {
                SplitShareList(
                    bill = bill,
                    rupiahFormat = rupiahFormat,
                    isBusy = state.isBusy,
                    payingShareId = state.payingShareId,
                    lastChangeGiven = state.lastChangeGiven,
                    onPayCash = onPayCash,
                    onPayQris = onPayQris
                )
            }
            SplitStage.OPEN_BILLS -> OpenSplitBillsContent(
                bills = state.openBills,
                rupiahFormat = rupiahFormat,
                isBusy = state.isBusy,
                onOpenBill = onOpenBill
            )
        }
    }
}

private enum class PlanMode { EQUAL, BY_ITEM }

/** Equal split by headcount, or whole order lines assigned to people. */
@Composable
private fun SplitPlanContent(
    bill: SplitBill,
    rupiahFormat: NumberFormat,
    isBusy: Boolean,
    onDivide: (SplitPlan) -> Unit
) {
    var mode by remember { mutableStateOf(PlanMode.EQUAL) }
    var people by remember { mutableIntStateOf(2) }
    val assignment = remember(bill.order.id) { mutableStateMapOf<String, Int>() }

    Column(verticalArrangement = Arrangement.spacedBy(CassyDimens.SpaceMd)) {
        Text(
            text = stringResource(R.string.split_total_label, rupiahFormat.format(bill.order.totalAmount)),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Row(horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceSm), modifier = Modifier.fillMaxWidth()) {
            val equal = mode == PlanMode.EQUAL
            if (equal) {
                CassyPrimaryButton(text = stringResource(R.string.split_mode_equal), onClick = {}, modifier = Modifier.weight(1f))
                CassySecondaryButton(text = stringResource(R.string.split_mode_by_item), onClick = { mode = PlanMode.BY_ITEM }, modifier = Modifier.weight(1f))
            } else {
                CassySecondaryButton(text = stringResource(R.string.split_mode_equal), onClick = { mode = PlanMode.EQUAL }, modifier = Modifier.weight(1f))
                CassyPrimaryButton(text = stringResource(R.string.split_mode_by_item), onClick = {}, modifier = Modifier.weight(1f))
            }
        }
        PeopleStepper(people = people, onChange = { next ->
            people = next
            assignment.entries.removeAll { it.value > next }
        })
        if (mode == PlanMode.BY_ITEM) {
            Text(text = stringResource(R.string.split_by_item_hint), style = MaterialTheme.typography.bodyMedium)
            bill.order.items.forEach { item ->
                ItemAssignmentRow(
                    item = item,
                    person = assignment[item.id] ?: 1,
                    onNext = { assignment[item.id] = (assignment[item.id] ?: 1) % people + 1 }
                )
            }
        } else {
            Text(text = stringResource(R.string.split_equal_hint), style = MaterialTheme.typography.bodyMedium)
        }
        val personLabel = stringResource(R.string.split_person_label_template)
        CassyPrimaryButton(
            text = stringResource(R.string.split_btn_divide),
            enabled = !isBusy,
            icon = Icons.AutoMirrored.Rounded.CallSplit,
            onClick = {
                val plan = when (mode) {
                    PlanMode.EQUAL -> SplitPlan.Equal(people)
                    PlanMode.BY_ITEM -> SplitPlan.ByItem(
                        (1..people).map { p ->
                            ShareAllocation(
                                label = personLabel.format(p),
                                items = bill.order.items
                                    .filter { (assignment[it.id] ?: 1) == p }
                                    .map { ItemAllocation(orderItemId = it.id, quantity = it.quantity) }
                            )
                        }
                    )
                }
                onDivide(plan)
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun PeopleStepper(people: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceMd)) {
        Text(text = stringResource(R.string.split_people_label), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        FilledTonalIconButton(onClick = { onChange(people - 1) }, enabled = people > MIN_PEOPLE, modifier = Modifier.heightIn(min = 48.dp)) {
            Icon(Icons.Rounded.Remove, contentDescription = stringResource(R.string.split_people_decrease))
        }
        Text(
            text = people.toString(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        FilledTonalIconButton(onClick = { onChange(people + 1) }, enabled = people < MAX_PEOPLE, modifier = Modifier.heightIn(min = 48.dp)) {
            Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.split_people_increase))
        }
    }
}

@Composable
private fun ItemAssignmentRow(item: PosOrderItem, person: Int, onNext: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceMd), modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.split_item_line, item.quantity, item.snapshotName),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        CassyTonalButton(text = stringResource(R.string.split_person_label_template).format(person), onClick = onNext)
    }
}

/** Shares with server totals; each collectable share offers cash or dynamic QRIS. */
@Composable
private fun SplitShareList(
    bill: SplitBill,
    rupiahFormat: NumberFormat,
    isBusy: Boolean,
    payingShareId: String?,
    lastChangeGiven: Long?,
    onPayCash: (String) -> Unit,
    onPayQris: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(CassyDimens.SpaceMd)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.split_remaining_label, rupiahFormat.format(bill.remainingAmount)),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            if (lastChangeGiven != null && lastChangeGiven > 0) {
                CassyStatusBadge(text = stringResource(R.string.split_change_label, rupiahFormat.format(lastChangeGiven)), tone = CassyToastTone.INFO)
            }
        }
        if (!bill.isFullyPaid) {
            CassyErrorBanner(message = stringResource(R.string.split_kitchen_waiting), tone = CassyToastTone.INFO)
        }
        bill.shares.forEach { share ->
            ShareCard(
                share = share,
                rupiahFormat = rupiahFormat,
                actionsEnabled = !isBusy && share.isCollectable,
                isPaying = payingShareId == share.id,
                onPayCash = { onPayCash(share.id) },
                onPayQris = { onPayQris(share.id) }
            )
        }
    }
}

@Composable
private fun ShareCard(
    share: SplitShare,
    rupiahFormat: NumberFormat,
    actionsEnabled: Boolean,
    isPaying: Boolean,
    onPayCash: () -> Unit,
    onPayQris: () -> Unit
) {
    CassyCard {
        Column(verticalArrangement = Arrangement.spacedBy(CassyDimens.SpaceSm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = share.label.ifBlank { stringResource(R.string.split_share_number, share.splitNumber) },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                CassyStatusBadge(
                    text = stringResource(shareStatusLabel(share)),
                    tone = if (share.isSettled) CassyToastTone.SUCCESS else CassyToastTone.WARNING,
                    icon = if (share.isSettled) Icons.Rounded.CheckCircle else null
                )
            }
            Text(
                text = stringResource(
                    R.string.split_share_amounts,
                    rupiahFormat.format(share.totalAmount),
                    rupiahFormat.format(share.paidAmount),
                    rupiahFormat.format(share.remainingAmount)
                ),
                style = MaterialTheme.typography.bodyMedium
            )
            if (!share.isSettled) {
                Row(horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceSm), modifier = Modifier.fillMaxWidth()) {
                    CassyPrimaryButton(
                        text = stringResource(if (isPaying) R.string.split_btn_processing else R.string.split_btn_pay_cash),
                        onClick = onPayCash,
                        enabled = actionsEnabled,
                        icon = Icons.Rounded.Payments,
                        modifier = Modifier.weight(1f)
                    )
                    CassyTonalButton(
                        text = stringResource(R.string.split_btn_pay_qris),
                        onClick = onPayQris,
                        enabled = actionsEnabled,
                        icon = Icons.Rounded.QrCode2,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

private fun shareStatusLabel(share: SplitShare): Int = when (share.status) {
    SplitShare.STATUS_PAID -> R.string.split_status_paid
    SplitShare.STATUS_PARTIALLY_PAID -> R.string.split_status_partial
    SplitShare.STATUS_PENDING -> R.string.split_status_pending
    else -> R.string.split_status_unpaid
}

@Composable
private fun OpenSplitBillsContent(
    bills: List<OpenSplitBillSummary>,
    rupiahFormat: NumberFormat,
    isBusy: Boolean,
    onOpenBill: (String) -> Unit
) {
    if (bills.isEmpty()) {
        CassyEmptyState(
            icon = Icons.Rounded.Restaurant,
            title = stringResource(if (isBusy) R.string.split_loading else R.string.split_open_bills_empty_title),
            subtitle = stringResource(R.string.split_open_bills_empty_subtitle)
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(CassyDimens.SpaceSm)) {
        bills.forEach { bill ->
            CassyCard(onClick = { onOpenBill(bill.orderId) }, enabled = !isBusy) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = listOf(bill.orderNumber, bill.tableNumber, bill.customerName).filter { it.isNotBlank() }.joinToString(" / "),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(R.string.split_total_label, rupiahFormat.format(bill.totalAmount)),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    CassyStatusBadge(
                        text = stringResource(R.string.split_remaining_label, rupiahFormat.format(bill.remainingAmount)),
                        tone = CassyToastTone.WARNING
                    )
                }
            }
        }
    }
}

private const val MIN_PEOPLE = 2
private const val MAX_PEOPLE = 20
