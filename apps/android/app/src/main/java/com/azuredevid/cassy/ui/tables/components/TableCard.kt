package com.azuredevid.cassy.ui.tables.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.azuredevid.cassy.R
import com.azuredevid.cassy.domain.model.TableEntry
import com.azuredevid.cassy.domain.model.TableStatus
import com.azuredevid.cassy.ui.designsystem.CassyCard
import com.azuredevid.cassy.ui.designsystem.CassyDimens
import com.azuredevid.cassy.ui.designsystem.CassyStatusBadge
import com.azuredevid.cassy.ui.designsystem.CassyToastTone

/**
 * One table on the management grid: name, section, capacity, and the server-derived occupancy as text
 * (status is never colour alone). Selecting it opens the QR panel.
 */
@Composable
fun TableCard(
    table: TableEntry,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    CassyCard(
        onClick = onClick,
        borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = CassyDimens.TouchTargetMin)
            .semantics {
                selected = isSelected
                role = Role.Button
            }
    ) {
        Text(
            text = table.displayName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (table.label.isNotBlank()) {
            Text(
                text = stringResource(R.string.tables_number_format, table.tableNumber),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceSm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Groups,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.tables_capacity_format, table.capacity),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            if (table.section.isNotBlank()) {
                Text(
                    text = table.section,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        TableStatusBadge(table)
    }
}

/** Occupancy badge shared by the card and the QR panel. Inactive tables say so before anything else. */
@Composable
fun TableStatusBadge(table: TableEntry, modifier: Modifier = Modifier) {
    val (textRes, tone) = when {
        !table.isActive -> R.string.tables_status_inactive to CassyToastTone.WARNING
        table.mergedIntoTableId != null -> R.string.tables_status_merged to CassyToastTone.INFO
        table.status == TableStatus.AVAILABLE -> R.string.tables_status_available to CassyToastTone.SUCCESS
        table.status == TableStatus.SETTLED -> R.string.tables_status_settled to CassyToastTone.INFO
        else -> R.string.tables_status_occupied to CassyToastTone.WARNING
    }
    CassyStatusBadge(text = stringResource(textRes), tone = tone, modifier = modifier)
}
