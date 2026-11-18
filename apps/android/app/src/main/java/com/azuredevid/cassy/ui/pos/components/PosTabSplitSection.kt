package com.azuredevid.cassy.ui.pos.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Remove
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
import com.azuredevid.cassy.ui.designsystem.CassyStatusBadge
import com.azuredevid.cassy.ui.designsystem.CassyToastTone
import com.azuredevid.cassy.ui.designsystem.CassyTonalButton
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
    onSelectSplit: (String) -> Unit
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
