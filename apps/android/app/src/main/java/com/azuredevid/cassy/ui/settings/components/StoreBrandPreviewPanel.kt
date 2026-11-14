package com.azuredevid.cassy.ui.settings.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.azuredevid.cassy.R
import com.azuredevid.cassy.ui.designsystem.CassyDimens
import com.azuredevid.cassy.ui.settings.StoreSettingsUiState
import com.azuredevid.cassy.ui.theme.CassyKunyit
import com.azuredevid.cassy.ui.theme.CassyPandan

internal fun parseHexColor(hex: String, fallback: Color): Color {
    val clean = hex.trim().removePrefix("#")
    if (clean.length != 6 && clean.length != 8) return fallback
    return try {
        val colorInt = clean.toLong(16)
        if (clean.length == 6) Color(colorInt or 0xFF000000) else Color(colorInt)
    } catch (_: Exception) {
        fallback
    }
}

@Composable
fun StoreBrandPreviewPanel(
    state: StoreSettingsUiState,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val primaryColor = remember(state.primaryHex) {
        parseHexColor(state.primaryHex, CassyPandan)
    }
    val accentColor = remember(state.accentHex) {
        parseHexColor(state.accentHex, CassyKunyit)
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CassyDimens.SpaceMd)
    ) {
        Text(
            text = stringResource(R.string.cms_preview_heading),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )

        Surface(
            modifier = Modifier
                .width(310.dp)
                .height(540.dp),
            shape = RoundedCornerShape(28.dp),
            color = colors.surface,
            border = BorderStroke(4.dp, colors.onSurface),
            shadowElevation = CassyDimens.ElevationModal
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(132.dp)
                        .background(primaryColor)
                ) {
                    if (state.heroBannerUrl.isNotBlank() && state.isHeroBannerUrlValid) {
                        AsyncImage(
                            model = state.heroBannerUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.matchParentSize()
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(Color.Black.copy(alpha = 0.4f))
                        )
                    }

                    Column(
                        modifier = Modifier
                            .matchParentSize()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Text(
                            text = state.heroTitleId.ifBlank { stringResource(R.string.cms_hero_title_id) },
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 15.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = state.heroSubtitleId.ifBlank { stringResource(R.string.cms_hero_subtitle_id) },
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = accentColor,
                                fontSize = 11.sp
                            )
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surfaceVariant)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = primaryColor
                    ) {
                        Text(
                            text = stringResource(R.string.cms_preview_cat_1),
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color.White,
                                fontSize = 10.sp
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = colors.surface,
                        border = BorderStroke(1.dp, colors.outline)
                    ) {
                        Text(
                            text = stringResource(R.string.cms_preview_cat_2),
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = colors.onSurfaceVariant,
                                fontSize = 10.sp
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = colors.surface,
                        border = BorderStroke(1.dp, colors.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.cms_preview_item_name),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = colors.onSurface
                                    )
                                )
                                Text(
                                    text = stringResource(R.string.cms_preview_item_price),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = accentColor,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 10.sp
                                    )
                                )
                            }
                            Surface(
                                shape = CircleShape,
                                color = primaryColor,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "+",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = primaryColor
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.cms_preview_cart_count),
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        )
                        Text(
                            text = stringResource(R.string.cms_preview_cart_cta),
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = accentColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(
                            if (!state.hidePoweredByBadge) {
                                R.string.cms_preview_powered_by
                            } else {
                                R.string.cms_preview_white_label
                            }
                        ),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = colors.onSurfaceVariant,
                            fontSize = 9.sp
                        )
                    )
                }
            }
        }
    }
}
