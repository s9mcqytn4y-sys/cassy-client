package com.azuredevid.cassy.ui.pairing.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.azuredevid.cassy.R
import com.azuredevid.cassy.domain.repository.ServerConnectionStatus
import com.azuredevid.cassy.domain.repository.ServerConnectivityState
import com.azuredevid.cassy.ui.designsystem.CassyDimens
import com.azuredevid.cassy.ui.theme.BrandPanelBorder
import com.azuredevid.cassy.ui.theme.BrandPanelPandan
import com.azuredevid.cassy.ui.theme.BrandPanelSurface
import com.azuredevid.cassy.ui.theme.BrandStatusCheckingDot
import com.azuredevid.cassy.ui.theme.BrandStatusOfflineDot
import com.azuredevid.cassy.ui.theme.BrandStatusOfflineText
import com.azuredevid.cassy.ui.theme.BrandStatusOnlineDot
import com.azuredevid.cassy.ui.theme.CassyPutihTulang
import com.azuredevid.cassy.ui.theme.CassyTinta

@Composable
fun StorePairingBrandPane(
    connectivity: ServerConnectivityState,
    onNavigateToServerConfig: () -> Unit,
    onRetryHealthProbe: () -> Unit,
    fillFullHeight: Boolean,
    modifier: Modifier = Modifier
) {
    val doodleBitmap = ImageBitmap.imageResource(id = R.drawable.bg_cassy_cafe_doodle)

    Column(
        modifier = modifier
            .clipToBounds()
            .background(BrandPanelPandan)
            .cassyCafePatternBackground(doodleBitmap)
            .padding(CassyDimens.Space2Xl),
        verticalArrangement = Arrangement.spacedBy(CassyDimens.SpaceLg)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceLg)
        ) {
            Surface(
                shape = CircleShape,
                color = Color.White,
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_cassy_monogram),
                        contentDescription = stringResource(R.string.a11y_cassy_logo),
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
            Column {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(Modifier.height(CassyDimens.SpaceXxs))
                Text(
                    text = stringResource(R.string.pairing_brand_tagline),
                    style = MaterialTheme.typography.bodyLarge,
                    color = CassyPutihTulang.copy(alpha = 0.88f)
                )
            }
        }

        Spacer(Modifier.height(CassyDimens.SpaceSm))

        Column(verticalArrangement = Arrangement.spacedBy(CassyDimens.SpaceMd)) {
            BrandCapabilityCard(
                icon = Icons.Rounded.Sync,
                text = stringResource(R.string.pairing_highlight_1)
            )
            BrandCapabilityCard(
                icon = Icons.Rounded.Print,
                text = stringResource(R.string.pairing_highlight_2)
            )
            BrandCapabilityCard(
                icon = Icons.Rounded.VerifiedUser,
                text = stringResource(R.string.pairing_highlight_3)
            )
        }

        if (fillFullHeight) {
            Spacer(Modifier.weight(1f))
        } else {
            Spacer(Modifier.height(CassyDimens.SpaceMd))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(CassyDimens.SpaceXxs)) {
                Button(
                    onClick = onNavigateToServerConfig,
                    shape = RoundedCornerShape(CassyDimens.RadiusButton),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CassyPutihTulang,
                        contentColor = CassyTinta
                    ),
                    contentPadding = PaddingValues(
                        horizontal = CassyDimens.SpaceLg,
                        vertical = CassyDimens.SpaceMd
                    ),
                    modifier = Modifier.defaultMinSize(
                        minWidth = CassyDimens.TouchTargetMin,
                        minHeight = CassyDimens.ButtonHeight
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(CassyDimens.IconSizeSm)
                    )
                    Spacer(Modifier.width(CassyDimens.SpaceSm))
                    Text(
                        text = stringResource(R.string.auth_btn_server_config),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (connectivity.status == ServerConnectionStatus.SERVER_UNREACHABLE ||
                    connectivity.status == ServerConnectionStatus.NETWORK_OFFLINE
                ) {
                    Text(
                        text = stringResource(R.string.pairing_server_offline_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = BrandStatusOfflineText,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(CassyDimens.RadiusBadge))
                            .clickable(role = Role.Button, onClick = onNavigateToServerConfig)
                            .padding(horizontal = CassyDimens.SpaceXs, vertical = CassyDimens.SpaceXxs)
                    )
                }
            }

            ServerStatusChip(
                connectivity = connectivity,
                onClick = onRetryHealthProbe
            )
        }
    }
}

@Composable
private fun BrandCapabilityCard(
    icon: ImageVector,
    text: String
) {
    Surface(
        shape = RoundedCornerShape(CassyDimens.RadiusCard),
        color = BrandPanelSurface,
        border = BorderStroke(1.dp, BrandPanelBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = CassyDimens.SpaceLg, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceMd)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = CassyPutihTulang,
                modifier = Modifier.size(CassyDimens.IconSizeLg)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = CassyPutihTulang
            )
        }
    }
}

@Composable
private fun ServerStatusChip(
    connectivity: ServerConnectivityState,
    onClick: () -> Unit
) {
    val (dotColor, label) = when (connectivity.status) {
        ServerConnectionStatus.ONLINE -> {
            val ms = connectivity.latencyMs
            val text = if (ms != null) {
                stringResource(R.string.pairing_server_status_online, ms)
            } else {
                stringResource(R.string.pairing_server_status_online_simple)
            }
            BrandStatusOnlineDot to text
        }
        ServerConnectionStatus.CHECKING -> {
            BrandStatusCheckingDot to stringResource(R.string.pairing_server_status_checking)
        }
        ServerConnectionStatus.NETWORK_OFFLINE -> {
            BrandStatusOfflineDot to stringResource(R.string.pairing_server_status_network_offline)
        }
        ServerConnectionStatus.INTERNET_UNVALIDATED -> {
            BrandStatusCheckingDot to stringResource(R.string.pairing_server_status_internet_unvalidated)
        }
        ServerConnectionStatus.SERVER_UNREACHABLE -> {
            BrandStatusOfflineDot to stringResource(R.string.pairing_server_status_server_unreachable)
        }
    }
    val isOffline = connectivity.status == ServerConnectionStatus.NETWORK_OFFLINE ||
        connectivity.status == ServerConnectionStatus.SERVER_UNREACHABLE
    val isWarning = connectivity.status == ServerConnectionStatus.INTERNET_UNVALIDATED
    val accessibilityDesc = stringResource(R.string.pairing_server_status_cd, label)

    Surface(
        shape = RoundedCornerShape(CassyDimens.RadiusPill),
        color = when {
            isOffline -> Color(0xFF471C1C).copy(alpha = 0.82f)
            isWarning -> Color(0xFF4D3812).copy(alpha = 0.84f)
            else -> BrandPanelSurface.copy(alpha = 0.78f)
        },
        border = BorderStroke(
            width = 1.dp,
            color = when {
                isOffline -> BrandStatusOfflineDot.copy(alpha = 0.72f)
                isWarning -> BrandStatusCheckingDot.copy(alpha = 0.76f)
                else -> BrandPanelBorder
            }
        ),
        modifier = Modifier
            .defaultMinSize(minHeight = CassyDimens.TouchTargetMin)
            .clip(RoundedCornerShape(CassyDimens.RadiusPill))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = accessibilityDesc }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = CassyDimens.SpaceMd, vertical = CassyDimens.SpaceSm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceSm)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isOffline) BrandStatusOfflineText else Color.White
            )
        }
    }
}

private fun Modifier.cassyCafePatternBackground(doodleBitmap: ImageBitmap): Modifier = drawWithCache {
    val shaderBrush = ShaderBrush(
        ImageShader(
            image = doodleBitmap,
            tileModeX = TileMode.Repeated,
            tileModeY = TileMode.Repeated
        )
    )
    val vignetteBrush = Brush.radialGradient(
        colors = listOf(
            BrandPanelPandan.copy(alpha = 0.45f),
            BrandPanelPandan.copy(alpha = 0.15f),
            BrandPanelPandan.copy(alpha = 0.58f)
        ),
        center = Offset(size.width * 0.45f, size.height * 0.42f),
        radius = size.maxDimension.coerceAtLeast(1f) * 0.75f
    )

    onDrawBehind {
        drawRect(brush = shaderBrush, alpha = 0.22f)
        drawRect(brush = vignetteBrush)
    }
}
