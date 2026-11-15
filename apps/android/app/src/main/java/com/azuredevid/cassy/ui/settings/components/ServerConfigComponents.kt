package com.azuredevid.cassy.ui.settings.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azuredevid.cassy.BuildConfig
import com.azuredevid.cassy.R
import com.azuredevid.cassy.domain.repository.DiscoveredLocalServer
import com.azuredevid.cassy.ui.designsystem.CassyCard
import com.azuredevid.cassy.ui.designsystem.CassyDimens
import com.azuredevid.cassy.ui.designsystem.CassyModalDialog
import com.azuredevid.cassy.ui.designsystem.CassyPrimaryButton
import com.azuredevid.cassy.ui.designsystem.CassySecondaryButton
import com.azuredevid.cassy.ui.designsystem.CassySectionHeader
import com.azuredevid.cassy.ui.settings.ServerConfigUiState
import com.azuredevid.cassy.ui.theme.CassyPandan

@Composable
fun EnvironmentSecurityBadge(
    enforceHttpsOnly: Boolean,
    modifier: Modifier = Modifier
) {
    val envName = BuildConfig.FLAVOR.uppercase()
    val label = if (enforceHttpsOnly) {
        stringResource(R.string.server_config_env_strict_https_format, envName)
    } else {
        stringResource(R.string.server_config_env_hybrid_tls_format, envName)
    }
    Surface(
        modifier = modifier.padding(end = CassyDimens.SpaceMd),
        shape = RoundedCornerShape(999.dp),
        color = Color(0xFFE6F0EC),
        border = BorderStroke(1.dp, Color(0xFFB7DEC8))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = CassyDimens.SpaceMd, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Dns,
                contentDescription = null,
                tint = CassyPandan,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = CassyPandan
            )
        }
    }
}

@Composable
fun ColumnScope.ServerConfigInfoPane(
    state: ServerConfigUiState,
    onDiscoverMdns: () -> Unit
) {
    CassyCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = CassyPandan,
        borderColor = Color(0xFF16443B)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceMd)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF7F5F0)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_cassy_monogram),
                    contentDescription = stringResource(R.string.a11y_cassy_logo),
                    modifier = Modifier.size(30.dp)
                )
            }
            Column {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF7F5F0)
                )
                Text(
                    text = stringResource(R.string.pairing_brand_tagline),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFD5E6E1)
                )
            }
        }
        Spacer(Modifier.height(CassyDimens.SpaceMd))
        Text(
            text = stringResource(R.string.server_config_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFF7F5F0)
        )
    }

    Spacer(Modifier.height(CassyDimens.SpaceLg))

    CassyCard(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
        containerColor = Color(0xFFECE9DF),
        borderColor = Color(0xFFD8D3C5)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            CassySectionHeader(title = stringResource(R.string.server_config_guide_title))
            Spacer(Modifier.height(CassyDimens.SpaceMd))

            GuideFeatureRow(
                icon = Icons.Rounded.CloudQueue,
                title = stringResource(R.string.server_config_guide_cloud_title),
                description = stringResource(R.string.server_config_guide_cloud_desc)
            )
            Spacer(Modifier.height(18.dp))
            GuideFeatureRow(
                icon = Icons.Rounded.Router,
                title = stringResource(R.string.server_config_guide_lan_title),
                description = stringResource(R.string.server_config_guide_lan_desc)
            )
            Spacer(Modifier.height(18.dp))
            GuideFeatureRow(
                icon = Icons.Rounded.Devices,
                title = stringResource(R.string.server_config_guide_emulator_title),
                description = stringResource(R.string.server_config_guide_emulator_desc)
            )
        }

        Spacer(Modifier.height(CassyDimens.SpaceMd))

        CassySecondaryButton(
            text = if (state.isDiscoveringMdns) {
                stringResource(R.string.server_config_discovering_mdns)
            } else {
                stringResource(R.string.server_config_discover_mdns)
            },
            onClick = onDiscoverMdns,
            loading = state.isDiscoveringMdns,
            icon = Icons.Rounded.TravelExplore,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = CassyPandan,
            borderColor = CassyPandan,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun GuideFeatureRow(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceMd)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFF17211E),
            modifier = Modifier
                .padding(top = 2.dp)
                .size(20.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF17211E)
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ConnectionDiagnosticsBox(
    state: ServerConfigUiState,
    modifier: Modifier = Modifier
) {
    val isConnected = state.probeStatusOk == true
    val isFailed = state.probeStatusOk == false
    val bgColor = when {
        isConnected -> Color(0xFFE6F4EA)
        isFailed -> Color(0xFFFDECEA)
        else -> Color(0xFFEAF3EE)
    }
    val borderColor = when {
        isConnected -> Color(0xFF4CAF50)
        isFailed -> Color(0xFFE57373)
        else -> Color(0xFFB7DEC8)
    }
    val statusText = when {
        state.isTesting -> stringResource(R.string.server_config_diag_status_testing)
        isConnected -> {
            val code = state.probeHttpCode ?: 200
            val latency = state.probeLatencyMs ?: 1L
            if (state.probeIsTls) {
                stringResource(R.string.server_config_status_connected_tls_format, code, latency)
            } else {
                stringResource(R.string.server_config_status_connected_lan_format, code, latency)
            }
        }
        isFailed -> stringResource(
            R.string.server_config_test_fail,
            state.probeErrorMessage ?: "Connection refused"
        )
        else -> stringResource(R.string.server_config_diag_status_ready)
    }
    val securityPolicyText = if (state.enforceHttpsOnly) {
        stringResource(R.string.server_config_diag_security_strict)
    } else {
        stringResource(R.string.server_config_diag_security_hybrid)
    }
    val lastVerifiedText = state.lastVerifiedEndpoint
        ?: stringResource(R.string.server_config_diag_not_tested)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CassyDimens.RadiusCard),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(CassyDimens.SpaceMd),
            verticalArrangement = Arrangement.spacedBy(CassyDimens.SpaceXs)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceSm)
            ) {
                val icon = when {
                    isConnected -> Icons.Rounded.CheckCircle
                    isFailed -> Icons.Rounded.ErrorOutline
                    else -> Icons.Rounded.Sync
                }
                val tint = when {
                    isConnected -> Color(0xFF1B5E20)
                    isFailed -> Color(0xFFB71C1C)
                    else -> CassyPandan
                }
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = stringResource(R.string.server_config_diag_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF17211E)
                )
            }

            Spacer(Modifier.height(CassyDimens.SpaceXs))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceLg)
            ) {
                DiagnosticMetricCell(
                    label = stringResource(R.string.server_config_diag_overline_status),
                    value = statusText,
                    valueColor = Color(0xFF17211E),
                    modifier = Modifier.weight(1f)
                )
                DiagnosticMetricCell(
                    label = stringResource(R.string.server_config_diag_overline_security),
                    value = securityPolicyText,
                    valueColor = Color(0xFF17211E),
                    modifier = Modifier.weight(1.35f)
                )
            }

            Spacer(Modifier.height(CassyDimens.SpaceXs))

            DiagnosticMetricCell(
                label = stringResource(R.string.server_config_diag_overline_verified),
                value = lastVerifiedText,
                valueColor = if (state.lastVerifiedEndpoint != null) Color(0xFF17211E) else Color(0xFF6B7873)
            )
        }
    }
}

@Composable
private fun DiagnosticMetricCell(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 0.8.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF4A5A54)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = valueColor
        )
    }
}

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
