package com.azuredevid.cassy.ui.kds.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.RoomService
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.azuredevid.cassy.R
import com.azuredevid.cassy.domain.model.KdsAgeLevel
import com.azuredevid.cassy.domain.model.KdsTicket
import com.azuredevid.cassy.domain.model.KdsTicketStatus
import com.azuredevid.cassy.domain.model.QRServiceMode
import com.azuredevid.cassy.ui.designsystem.CassyCard
import com.azuredevid.cassy.ui.designsystem.CassyDimens
import com.azuredevid.cassy.ui.designsystem.CassyPrimaryButton
import com.azuredevid.cassy.ui.designsystem.CassyStatusBadge
import com.azuredevid.cassy.ui.designsystem.CassyTextActionButton
import com.azuredevid.cassy.ui.designsystem.CassyToastTone

/** Tabular figures so queue numbers and ages do not shift width as they change. */
private val tabular = TextStyle(fontFeatureSettings = "tnum")

/**
 * One kitchen ticket sized for reading at about 2 m: queue number in display type, lines in title type,
 * an age badge that pairs colour with text (on time, attention, late), and one large bump button.
 * The card border follows the age band so a late ticket stands out even from across the kitchen.
 */
@Composable
fun KdsTicketCard(
    ticket: KdsTicket,
    nowMillis: Long,
    serviceMode: QRServiceMode,
    canBump: Boolean,
    isPending: Boolean,
    onBump: () -> Unit,
    onRecall: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ageMinutes = ticket.ageMinutes(nowMillis)
    val ageLevel = KdsAgeLevel.of(ageMinutes)
    val (ageTone, ageLabelRes) = when (ageLevel) {
        KdsAgeLevel.ON_TIME -> CassyToastTone.SUCCESS to R.string.kds_age_on_time
        KdsAgeLevel.WARNING -> CassyToastTone.WARNING to R.string.kds_age_warning
        KdsAgeLevel.LATE -> CassyToastTone.ERROR to R.string.kds_age_late
    }
    val borderColor = when (ageLevel) {
        KdsAgeLevel.ON_TIME -> MaterialTheme.colorScheme.outline
        KdsAgeLevel.WARNING -> MaterialTheme.colorScheme.tertiary
        KdsAgeLevel.LATE -> MaterialTheme.colorScheme.error
    }
    val queueLabel = ticket.queueNumber.ifBlank { ticket.orderNumber }
    val a11yTicket = stringResource(R.string.kds_a11y_ticket, queueLabel, ageMinutes)

    CassyCard(
        borderColor = borderColor,
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = a11yTicket }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(CassyDimens.SpaceSm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = queueLabel,
                    style = MaterialTheme.typography.displaySmall.merge(tabular),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                CassyStatusBadge(
                    text = stringResource(ageLabelRes, ageMinutes),
                    tone = ageTone,
                    icon = Icons.Rounded.Schedule
                )
            }
            Text(
                text = ticketContext(ticket),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (ticket.latePayment) {
                CassyStatusBadge(text = stringResource(R.string.kds_late_payment), tone = CassyToastTone.INFO)
            }
            ticket.lines.forEach { line ->
                Column {
                    Text(
                        text = stringResource(R.string.kds_line_format, line.quantity, line.name) +
                            if (line.variant.isNotBlank()) " (${line.variant})" else "",
                        style = MaterialTheme.typography.titleLarge.merge(tabular),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    line.options.forEach { option ->
                        Text(
                            text = option,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (line.notes.isNotBlank()) {
                        Text(
                            text = stringResource(R.string.kds_line_note, line.notes),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            if (canBump) {
                val (labelRes, icon) = bumpLabel(ticket.status, serviceMode)
                if (labelRes != null) {
                    CassyPrimaryButton(
                        text = stringResource(labelRes),
                        icon = icon,
                        onClick = onBump,
                        enabled = !isPending,
                        loading = isPending,
                        height = CassyDimens.ButtonHeightTall,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (ticket.status == KdsTicketStatus.READY) {
                    CassyTextActionButton(
                        text = stringResource(R.string.kds_btn_recall),
                        icon = Icons.AutoMirrored.Rounded.Undo,
                        onClick = onRecall,
                        enabled = !isPending,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun ticketContext(ticket: KdsTicket): String {
    val table = if (ticket.tableNumber.isNotBlank()) {
        stringResource(R.string.kds_table_format, ticket.tableNumber)
    } else {
        stringResource(R.string.kds_takeaway)
    }
    val round = ticket.roundNumber?.takeIf { it > 1 }?.let { stringResource(R.string.kds_round_format, it) }
    return listOfNotNull(table, round).joinToString(" · ")
}

/** Button label and icon for the next step; served wording follows the outlet's QR service mode (US3/US4). */
private fun bumpLabel(status: KdsTicketStatus, mode: QRServiceMode) = when (status) {
    KdsTicketStatus.QUEUED -> R.string.kds_btn_start to Icons.Rounded.LocalFireDepartment
    KdsTicketStatus.PREPARING -> R.string.kds_btn_ready to Icons.Rounded.CheckCircle
    KdsTicketStatus.READY -> (if (mode == QRServiceMode.TABLE) R.string.kds_btn_served_table else R.string.kds_btn_served_pickup) to
        Icons.Rounded.RoomService
    KdsTicketStatus.SERVED, KdsTicketStatus.CANCELLED -> null to null
}
