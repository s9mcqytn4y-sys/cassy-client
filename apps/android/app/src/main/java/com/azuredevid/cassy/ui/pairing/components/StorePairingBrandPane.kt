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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.azuredevid.cassy.ui.theme.BrandPanelMotif
import com.azuredevid.cassy.ui.theme.BrandPanelPandan
import com.azuredevid.cassy.ui.theme.BrandPanelSurface
import com.azuredevid.cassy.ui.theme.BrandStatusCheckingDot
import com.azuredevid.cassy.ui.theme.BrandStatusOfflineDot
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
    Column(
        modifier = modifier
            .background(BrandPanelPandan)
            .cassyCafePatternBackground(BrandPanelMotif)
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
        ServerConnectionStatus.SERVER_UNREACHABLE -> {
            BrandStatusOfflineDot to stringResource(R.string.pairing_server_status_server_unreachable)
        }
    }
    val accessibilityDesc = stringResource(R.string.pairing_server_status_cd, label)

    Row(
        modifier = Modifier
            .defaultMinSize(minHeight = CassyDimens.TouchTargetMin)
            .clip(RoundedCornerShape(CassyDimens.RadiusPill))
            .background(BrandPanelSurface.copy(alpha = 0.72f))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = accessibilityDesc }
            .padding(horizontal = CassyDimens.SpaceMd, vertical = CassyDimens.SpaceSm),
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
            color = Color.White
        )
    }
}

private fun Modifier.cassyCafePatternBackground(motifColor: Color): Modifier = drawWithCache {
    val tilePx = 112.dp.toPx()
    val strokePx = 1.6.dp.toPx()
    val stroke = Stroke(width = strokePx, cap = StrokeCap.Round)
    val ink = motifColor.copy(alpha = 0.34f)
    val cols = (size.width / tilePx).toInt() + 2
    val rows = (size.height / tilePx).toInt() + 2

    onDrawBehind {
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val originX = col * tilePx + if (row % 2 == 1) tilePx * 0.45f else 0f
                val originY = row * tilePx
                when ((col + row) % 3) {
                    0 -> drawEspressoMotif(originX, originY, tilePx, ink, stroke)
                    1 -> drawPlateForkMotif(originX, originY, tilePx, ink, stroke)
                    else -> drawTakeawayCupMotif(originX, originY, tilePx, ink, stroke)
                }
            }
        }
    }
}

private fun DrawScope.drawEspressoMotif(
    ox: Float,
    oy: Float,
    tile: Float,
    color: Color,
    stroke: Stroke
) {
    val w = tile * 0.30f
    val h = tile * 0.24f
    val cx = ox + tile * 0.28f
    val cy = oy + tile * 0.38f
    drawRoundRect(
        color = color,
        topLeft = Offset(cx, cy),
        size = Size(w, h),
        cornerRadius = CornerRadius(h * 0.32f, h * 0.32f),
        style = stroke
    )
    drawLine(
        color = color,
        start = Offset(cx - w * 0.14f, cy + h + 4.dp.toPx()),
        end = Offset(cx + w * 1.14f, cy + h + 4.dp.toPx()),
        strokeWidth = stroke.width,
        cap = StrokeCap.Round
    )
    val steam = Path().apply {
        moveTo(cx + w * 0.35f, cy - 3.dp.toPx())
        quadraticTo(cx + w * 0.20f, cy - 10.dp.toPx(), cx + w * 0.42f, cy - 15.dp.toPx())
        moveTo(cx + w * 0.65f, cy - 3.dp.toPx())
        quadraticTo(cx + w * 0.80f, cy - 10.dp.toPx(), cx + w * 0.58f, cy - 15.dp.toPx())
    }
    drawPath(steam, color = color, style = stroke)
}

private fun DrawScope.drawPlateForkMotif(
    ox: Float,
    oy: Float,
    tile: Float,
    color: Color,
    stroke: Stroke
) {
    val r = tile * 0.14f
    val center = Offset(ox + tile * 0.48f, oy + tile * 0.48f)
    drawCircle(color = color, radius = r, center = center, style = stroke)
    drawCircle(color = color, radius = r * 0.52f, center = center, style = stroke)
}

private fun DrawScope.drawTakeawayCupMotif(
    ox: Float,
    oy: Float,
    tile: Float,
    color: Color,
    stroke: Stroke
) {
    val cx = ox + tile * 0.36f
    val cy = oy + tile * 0.34f
    val topW = tile * 0.24f
    val botW = tile * 0.17f
    val h = tile * 0.30f
    val inset = (topW - botW) / 2f
    val cup = Path().apply {
        moveTo(cx, cy)
        lineTo(cx + topW, cy)
        lineTo(cx + topW - inset, cy + h)
        lineTo(cx + inset, cy + h)
        close()
    }
    drawPath(cup, color = color, style = stroke)
    drawLine(
        color = color,
        start = Offset(cx - 3.dp.toPx(), cy),
        end = Offset(cx + topW + 3.dp.toPx(), cy),
        strokeWidth = stroke.width,
        cap = StrokeCap.Round
    )
}
