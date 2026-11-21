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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.TravelExplore
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
import com.azuredevid.cassy.BuildConfig
import com.azuredevid.cassy.R
import com.azuredevid.cassy.ui.designsystem.CassyCard
import com.azuredevid.cassy.ui.designsystem.CassyDimens
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
    onDiscoverMdns: () -> Unit,
    isSplitPane: Boolean = true
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
        if (!isSplitPane) {
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

    if (isSplitPane) {
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
