package com.azuredevid.cassy.ui.pos.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.azuredevid.cassy.R
import com.azuredevid.cassy.domain.repository.ServiceRequest
import com.azuredevid.cassy.ui.designsystem.CassyDimens
import com.azuredevid.cassy.ui.designsystem.CassyModalDialog
import com.azuredevid.cassy.ui.designsystem.CassyPrimaryButton
import com.azuredevid.cassy.ui.designsystem.CassySecondaryButton
import com.azuredevid.cassy.ui.theme.statusWarnBg
import com.azuredevid.cassy.ui.theme.statusWarnFg
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Kunyit pill that appears only while guests are waiting; one tap opens the queue. */
@Composable
fun PosServiceRequestPill(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(CassyDimens.RadiusButton),
        color = statusWarnBg(),
        contentColor = statusWarnFg(),
        shadowElevation = CassyDimens.ElevationModal,
        modifier = modifier.defaultMinSize(minHeight = CassyDimens.TouchTargetMin)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = CassyDimens.SpaceLg, vertical = CassyDimens.SpaceSm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.NotificationsActive, contentDescription = null, modifier = Modifier.size(CassyDimens.IconSizeSm))
            Spacer(Modifier.width(CassyDimens.SpaceSm))
            Text(
                text = pluralStringResource(R.plurals.service_pill_count, count, count),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/** The waiting queue: oldest first, "Ambil" to claim, "Selesai" to close. */
@Composable
fun PosServiceRequestsModal(
    requests: List<ServiceRequest>,
    busyRequestId: String?,
    onAcknowledge: (String) -> Unit,
    onDone: (String) -> Unit,
    onDismiss: () -> Unit,
    onCancelMute: (String) -> Unit = {}
) {
    CassyModalDialog(
        title = stringResource(R.string.service_panel_title),
        icon = Icons.Rounded.NotificationsActive,
        maxWidth = CassyDimens.ModalWidthStandard,
        onDismissRequest = onDismiss
    ) {
        Text(
            text = stringResource(R.string.service_cooldown_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (requests.isEmpty()) {
            Text(
                text = stringResource(R.string.service_panel_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        requests.forEachIndexed { index, request ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            // Text on top, actions below: on a phone the buttons beside the text squeezed the table name away.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = CassyDimens.SpaceSm),
                verticalArrangement = Arrangement.spacedBy(CassyDimens.SpaceSm)
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.service_row_title, request.tableNumber, request.label),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    val status = if (request.isAcknowledged) {
                        stringResource(R.string.service_row_taken, clockOf(request.createdAt))
                    } else {
                        stringResource(R.string.service_row_waiting, clockOf(request.createdAt))
                    }
                    Text(status, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    request.note?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                val busy = busyRequestId != null
                Row(
                    modifier = Modifier.align(Alignment.End),
                    horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceSm)
                ) {
                    CassySecondaryButton(
                        text = stringResource(R.string.service_btn_mute),
                        onClick = { onCancelMute(request.id) },
                        enabled = !busy
                    )
                    if (!request.isAcknowledged) {
                        CassySecondaryButton(
                            text = stringResource(R.string.service_btn_take),
                            onClick = { onAcknowledge(request.id) },
                            enabled = !busy
                        )
                    }
                    CassyPrimaryButton(
                        text = stringResource(R.string.service_btn_done),
                        onClick = { onDone(request.id) },
                        enabled = !busy
                    )
                }
            }
        }
    }
}

private val clockFormat = DateTimeFormatter.ofPattern("HH:mm")

private fun clockOf(rfc3339: String): String =
    runCatching { OffsetDateTime.parse(rfc3339).atZoneSameInstant(ZoneId.systemDefault()).format(clockFormat) }
        .getOrDefault("")
