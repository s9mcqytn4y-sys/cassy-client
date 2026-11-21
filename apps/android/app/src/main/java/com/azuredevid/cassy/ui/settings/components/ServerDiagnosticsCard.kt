package com.azuredevid.cassy.ui.settings.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Sync
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
import androidx.compose.ui.unit.sp
import com.azuredevid.cassy.R
import com.azuredevid.cassy.ui.designsystem.CassyDimens
import com.azuredevid.cassy.ui.settings.ServerConfigUiState
import com.azuredevid.cassy.ui.theme.CassyPandan

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
            state.probeErrorMessage?.asString() ?: stringResource(R.string.server_msg_connection_refused)
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
