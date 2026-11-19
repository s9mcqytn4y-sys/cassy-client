package com.azuredevid.cassy.ui.pos.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.automirrored.rounded.CallSplit
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.azuredevid.cassy.R
import com.azuredevid.cassy.domain.repository.TabSplit
import com.azuredevid.cassy.ui.designsystem.CassyDimens
import com.azuredevid.cassy.ui.designsystem.CassyModalDialog
import com.azuredevid.cassy.ui.designsystem.CassyPrimaryButton
import com.azuredevid.cassy.ui.designsystem.CassySecondaryButton
import com.azuredevid.cassy.ui.designsystem.CassyStatusBadge
import com.azuredevid.cassy.ui.designsystem.CassyToastTone
import com.azuredevid.cassy.ui.designsystem.CassyTonalButton
import com.azuredevid.cassy.ui.pos.ItemSplitDraft
import java.text.NumberFormat

/**
 * Equal split of a table bill. Before closing, the cashier picks how many guests share the bill; after
 * the server returns the shares, each one is picked and paid in turn. Amounts are the server's.
 */
@Composable
fun PosTabSplitSection(
    splitCount: Int,
    splits: List<TabSplit>,
    selectedSplitId: String?,
    isBusy: Boolean,
    rupiahFormat: NumberFormat,
    onSplitCountChange: (Int) -> Unit,
    onSplit: () -> Unit,
    onSelectSplit: (String) -> Unit,
    canSplitByItem: Boolean = false,
    onSplitByItem: () -> Unit = {}
) {
    if (splits.size > 1) {
        SplitShares(splits, selectedSplitId, isBusy, rupiahFormat, onSelectSplit)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(CassyDimens.SpaceSm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceSm)
        ) {
            Text(
                text = stringResource(R.string.tab_split_label),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            val guests = pluralStringResource(R.plurals.tab_split_guests, splitCount, splitCount)
            StepButton(
                icon = Icons.Rounded.Remove,
                description = stringResource(R.string.tab_split_a11y_fewer),
                enabled = !isBusy && splitCount > 1,
                onClick = { onSplitCountChange(splitCount - 1) }
            )
            Text(
                text = if (splitCount > 1) guests else stringResource(R.string.tab_split_none),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 88.dp)
            )
            StepButton(
                icon = Icons.Rounded.Add,
                description = stringResource(R.string.tab_split_a11y_more),
                enabled = !isBusy && splitCount < MAX_SHARES,
                onClick = { onSplitCountChange(splitCount + 1) }
            )
        }
        if (splitCount > 1) {
            CassyTonalButton(
                text = stringResource(R.string.tab_split_btn, splitCount),
                onClick = onSplit,
                enabled = !isBusy,
                icon = Icons.AutoMirrored.Rounded.CallSplit,
                modifier = Modifier.fillMaxWidth()
            )
        }
        CassySecondaryButton(
            text = stringResource(R.string.tab_split_by_item_btn),
            onClick = onSplitByItem,
            enabled = !isBusy && canSplitByItem,
            icon = Icons.AutoMirrored.Rounded.ReceiptLong,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Split by item: each unit of each sent line goes to one guest. Shares are priced by the server, so
 * the dialog only shows who pays for what.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PosItemSplitDialog(
    draft: ItemSplitDraft,
    isBusy: Boolean,
    onGuestsChange: (Int) -> Unit,
    onAssign: (unitIndex: Int, guest: Int) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    CassyModalDialog(
        title = stringResource(R.string.tab_split_by_item_title),
        subtitle = stringResource(R.string.tab_split_by_item_subtitle),
        icon = Icons.AutoMirrored.Rounded.ReceiptLong,
        dismissEnabled = !isBusy,
        onDismissRequest = onDismiss,
        footer = {
            CassySecondaryButton(
                text = stringResource(R.string.btn_cancel),
                onClick = onDismiss,
                enabled = !isBusy,
                modifier = Modifier.weight(1f)
            )
            CassyPrimaryButton(
                text = stringResource(R.string.tab_split_btn, draft.guests),
                onClick = onConfirm,
                enabled = draft.canConfirm && !isBusy,
                loading = isBusy,
                modifier = Modifier.weight(1f)
            )
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceSm)
        ) {
            Text(
                text = stringResource(R.string.tab_split_by_item_guests),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            StepButton(
                icon = Icons.Rounded.Remove,
                description = stringResource(R.string.tab_split_a11y_fewer),
                enabled = !isBusy && draft.guests > 2,
                onClick = { onGuestsChange(draft.guests - 1) }
            )
            Text(
                text = pluralStringResource(R.plurals.tab_split_guests, draft.guests, draft.guests),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 88.dp)
            )
            StepButton(
                icon = Icons.Rounded.Add,
                description = stringResource(R.string.tab_split_a11y_more),
                enabled = !isBusy && draft.guests < minOf(MAX_SHARES, draft.units.size),
                onClick = { onGuestsChange(draft.guests + 1) }
            )
        }
        draft.units.forEachIndexed { index, unit ->
            Column(verticalArrangement = Arrangement.spacedBy(CassyDimens.SpaceXs)) {
                Text(text = unit.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                FlowRow(
                    modifier = Modifier.selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceXs)
                ) {
                    (1..draft.guests).forEach { guest ->
                        val guestLabel = stringResource(R.string.tab_split_guest, guest)
                        FilterChip(
                            selected = draft.owners[index] == guest,
                            onClick = { onAssign(index, guest) },
                            enabled = !isBusy,
                            label = { Text(guestLabel) },
                            modifier = Modifier.defaultMinSize(minHeight = CassyDimens.TouchTargetMin)
                        )
                    }
                }
            }
        }
        if (draft.isFullyAssigned && !draft.canConfirm) {
            Text(
                text = stringResource(R.string.tab_split_by_item_empty_guest),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun SplitShares(
    splits: List<TabSplit>,
    selectedSplitId: String?,
    isBusy: Boolean,
    rupiahFormat: NumberFormat,
    onSelectSplit: (String) -> Unit
) {
    val paidCount = splits.count { it.isPaid }
    Column(
        modifier = Modifier.selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(CassyDimens.SpaceXs)
    ) {
        Text(
            text = stringResource(R.string.tab_split_progress, paidCount, splits.size),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        splits.forEach { split ->
            val selected = split.id == selectedSplitId
            val colors = MaterialTheme.colorScheme
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = CassyDimens.TouchTargetMin)
                    .selectable(
                        selected = selected,
                        enabled = !isBusy && !split.isPaid,
                        role = Role.RadioButton,
                        onClick = { onSelectSplit(split.id) }
                    ),
                shape = RoundedCornerShape(CassyDimens.RadiusButton),
                color = if (selected) colors.surfaceVariant else colors.surface,
                border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) colors.primary else colors.outline)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = CassyDimens.SpaceMd, vertical = CassyDimens.SpaceSm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceSm)
                ) {
                    Text(
                        text = stringResource(R.string.tab_split_share, split.number),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.weight(1f)
                    )
                    if (split.isPaid) {
                        CassyStatusBadge(
                            text = stringResource(R.string.tab_split_paid),
                            tone = CassyToastTone.SUCCESS,
                            icon = Icons.Rounded.CheckCircle
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.cash_chip_rp, rupiahFormat.format(split.remainingAmount)),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (selected) colors.primary else colors.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StepButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(CassyDimens.RadiusButton),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier
            .size(CassyDimens.TouchTargetMin)
            .semantics { contentDescription = description }
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
    }
}

// The server accepts 2 to 20 equal shares.
private const val MAX_SHARES = 20
