package com.azuredevid.cassy.ui.settings.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.azuredevid.cassy.R
import com.azuredevid.cassy.domain.repository.DiscoveredLocalServer
import com.azuredevid.cassy.ui.designsystem.CassyDimens
import com.azuredevid.cassy.ui.designsystem.CassyModalDialog
import com.azuredevid.cassy.ui.designsystem.CassyPrimaryButton
import com.azuredevid.cassy.ui.designsystem.CassySecondaryButton
import com.azuredevid.cassy.ui.theme.CassyPandan

@Composable
fun MdnsDiscoveryDialog(
    isDiscovering: Boolean,
    discoveredServers: List<DiscoveredLocalServer>,
    onSelectServer: (String) -> Unit,
    onRescan: () -> Unit,
    onDismiss: () -> Unit
) {
    CassyModalDialog(
        title = stringResource(R.string.server_config_mdns_dialog_title),
        subtitle = stringResource(R.string.server_config_mdns_dialog_subtitle),
        icon = Icons.Rounded.TravelExplore,
        onDismissRequest = onDismiss,
        footer = {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceXs)
            ) {
                if (isDiscovering) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = CassyPandan
                    )
                    Text(
                        text = stringResource(R.string.server_config_mdns_scanning_footer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            CassySecondaryButton(
                text = stringResource(R.string.pairing_camera_permission_cancel),
                onClick = onDismiss
            )
            CassyPrimaryButton(
                text = stringResource(R.string.server_config_mdns_rescan),
                onClick = onRescan,
                loading = isDiscovering,
                icon = Icons.Rounded.Refresh
            )
        }
    ) {
        if (discoveredServers.isEmpty() && isDiscovering) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = CassyDimens.SpaceMd),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceMd)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.5.dp,
                    color = CassyPandan
                )
                Text(
                    text = stringResource(R.string.server_config_discovering_mdns),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else if (discoveredServers.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(CassyDimens.RadiusButton),
                color = Color(0xFFECE9DF),
                border = BorderStroke(1.dp, Color(0xFFD8D3C5))
            ) {
                Text(
                    text = stringResource(R.string.server_config_mdns_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(CassyDimens.SpaceMd)
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(CassyDimens.SpaceSm)) {
                for (server in discoveredServers) {
                    MdnsServerResultRow(
                        server = server,
                        onSelect = { onSelectServer(server.baseUrl) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MdnsServerResultRow(
    server: DiscoveredLocalServer,
    onSelect: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CassyDimens.RadiusButton),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, CassyPandan)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(CassyDimens.SpaceMd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceMd)
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = Color(0xFFEAF3EE)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Router,
                        contentDescription = null,
                        tint = CassyPandan,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = server.serviceName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = server.baseUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                server.latencyMs?.let { latency ->
                    Spacer(Modifier.height(2.dp))
                    MdnsSignalLatencyBadge(latencyMs = latency)
                }
            }
            CassyPrimaryButton(
                text = stringResource(R.string.server_config_mdns_use_btn),
                onClick = onSelect,
                modifier = Modifier.defaultMinSize(minHeight = CassyDimens.TouchTargetMin)
            )
        }
    }
}

@Composable
private fun MdnsSignalLatencyBadge(latencyMs: Long) {
    val signalRes = when {
        latencyMs <= 50L -> R.string.server_config_mdns_signal_strong
        latencyMs <= 150L -> R.string.server_config_mdns_signal_moderate
        else -> R.string.server_config_mdns_signal_weak
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFECE9DF)
    ) {
        Text(
            text = "${stringResource(signalRes)} • ${stringResource(R.string.server_config_mdns_latency, latencyMs)}",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF2B3834),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}
