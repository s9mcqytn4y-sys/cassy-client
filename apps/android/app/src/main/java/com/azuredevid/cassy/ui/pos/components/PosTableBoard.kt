package com.azuredevid.cassy.ui.pos.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.azuredevid.cassy.R
import com.azuredevid.cassy.domain.repository.PosTable
import com.azuredevid.cassy.ui.designsystem.CassyDimens
import com.azuredevid.cassy.ui.theme.statusOkBg
import com.azuredevid.cassy.ui.theme.statusWarnBg
import java.text.NumberFormat

/**
 * Table board for the dine-in picker: one tile per table with its state and unpaid bill. Tapping an
 * occupied table resumes its open bill; tiles are 104 x 88 dp so a cashier hits them without aiming.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PosTableBoard(
    tables: List<PosTable>,
    isLoading: Boolean,
    selectedTableId: String?,
    rupiahFormat: NumberFormat,
    onTableSelected: (PosTable) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(CassyDimens.SpaceSm)) {
        Text(
            text = stringResource(R.string.order_table_board_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        if (isLoading && tables.isEmpty()) {
            Text(
                text = stringResource(R.string.order_table_board_loading),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return
        }
        FlowRow(
            modifier = Modifier
                .heightIn(max = 320.dp)
                .verticalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceSm),
            verticalArrangement = Arrangement.spacedBy(CassyDimens.SpaceSm)
        ) {
            tables.forEach { table ->
                TableTile(table, table.id == selectedTableId, rupiahFormat) { onTableSelected(table) }
            }
        }
    }
}

@Composable
private fun TableTile(table: PosTable, selected: Boolean, rupiahFormat: NumberFormat, onClick: () -> Unit) {
    val container = when (table.status) {
        "occupied" -> statusWarnBg()
        "settled" -> statusOkBg()
        else -> MaterialTheme.colorScheme.surface
    }
    val statusLabel = when (table.status) {
        "occupied" -> stringResource(R.string.order_table_status_occupied)
        "settled" -> stringResource(R.string.order_table_status_settled)
        else -> stringResource(R.string.order_table_status_available)
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(CassyDimens.RadiusCard),
        color = container,
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        ),
        modifier = Modifier.size(width = 104.dp, height = 88.dp)
    ) {
        Column(modifier = Modifier.padding(CassyDimens.SpaceSm), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = table.tableNumber,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(text = statusLabel, style = MaterialTheme.typography.labelSmall)
            if (table.unpaidAmount > 0) {
                Text(
                    text = stringResource(R.string.order_table_unpaid, rupiahFormat.format(table.unpaidAmount)),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
