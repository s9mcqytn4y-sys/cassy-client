package com.azuredevid.cassy.ui.settings.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.azuredevid.cassy.R
import com.azuredevid.cassy.ui.designsystem.CassyCard
import com.azuredevid.cassy.ui.designsystem.CassyDimens
import com.azuredevid.cassy.ui.designsystem.CassyPrimaryButton
import com.azuredevid.cassy.ui.designsystem.CassySectionHeader
import com.azuredevid.cassy.ui.designsystem.CassyTextField
import com.azuredevid.cassy.ui.settings.StoreSettingsUiEvent
import com.azuredevid.cassy.ui.settings.StoreSettingsUiState
import com.azuredevid.cassy.ui.theme.CassyKunyit
import com.azuredevid.cassy.ui.theme.CassyTinta

private data class BrandPreset(
    val labelRes: Int,
    val hex: String
)

private val COLOR_PRESETS = listOf(
    BrandPreset(R.string.cms_preset_neutral, ""),
    BrandPreset(R.string.cms_preset_pandan, "#1F5F52"),
    BrandPreset(R.string.cms_preset_kunyit, "#E9A23B"),
    BrandPreset(R.string.cms_preset_tinta, "#17211E"),
    BrandPreset(R.string.cms_preset_batu, "#3E4C47")
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StoreBrandCmsTab(
    state: StoreSettingsUiState,
    onEvent: (StoreSettingsUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val colors = MaterialTheme.colorScheme

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(CassyDimens.SpaceXl),
        horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceXl)
    ) {
        Column(
            modifier = Modifier
                .weight(0.6f)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(CassyDimens.SpaceLg)
        ) {
            CassyCard {
                CassySectionHeader(
                    title = stringResource(R.string.cms_section_title),
                    subtitle = stringResource(R.string.cms_section_desc)
                )
            }

            CassyCard {
                Column(verticalArrangement = Arrangement.spacedBy(CassyDimens.SpaceMd)) {
                    CassySectionHeader(title = stringResource(R.string.cms_header_card_title))
                    CassyTextField(
                        value = state.heroTitleId,
                        onValueChange = { onEvent(StoreSettingsUiEvent.UpdateHeroTitleId(it)) },
                        label = stringResource(R.string.cms_hero_title_id)
                    )
                    CassyTextField(
                        value = state.heroTitleEn,
                        onValueChange = { onEvent(StoreSettingsUiEvent.UpdateHeroTitleEn(it)) },
                        label = stringResource(R.string.cms_hero_title_en)
                    )
                    CassyTextField(
                        value = state.heroSubtitleId,
                        onValueChange = { onEvent(StoreSettingsUiEvent.UpdateHeroSubtitleId(it)) },
                        label = stringResource(R.string.cms_hero_subtitle_id)
                    )
                    CassyTextField(
                        value = state.heroSubtitleEn,
                        onValueChange = { onEvent(StoreSettingsUiEvent.UpdateHeroSubtitleEn(it)) },
                        label = stringResource(R.string.cms_hero_subtitle_en)
                    )
                }
            }

            CassyCard {
                Column(verticalArrangement = Arrangement.spacedBy(CassyDimens.SpaceMd)) {
                    CassySectionHeader(
                        title = stringResource(R.string.cms_colors_title),
                        subtitle = stringResource(R.string.cms_presets_label)
                    )

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceSm),
                        verticalArrangement = Arrangement.spacedBy(CassyDimens.SpaceSm)
                    ) {
                        COLOR_PRESETS.forEach { preset ->
                            val presetColor = parseHexColor(preset.hex, CassyTinta)
                            val isSelected = state.primaryHex.equals(preset.hex, ignoreCase = true)
                            Surface(
                                shape = RoundedCornerShape(CassyDimens.RadiusBadge),
                                color = if (isSelected) colors.surfaceVariant else Color.Transparent,
                                border = BorderStroke(
                                    if (isSelected) 2.dp else 1.dp,
                                    if (isSelected) colors.primary else colors.outline
                                ),
                                modifier = Modifier
                                    .defaultMinSize(minHeight = CassyDimens.TouchTargetMin)
                                    .clip(RoundedCornerShape(CassyDimens.RadiusBadge))
                                    .clickable(role = Role.RadioButton) {
                                        onEvent(StoreSettingsUiEvent.UpdatePrimaryHex(preset.hex))
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(presetColor)
                                    )
                                    Text(
                                        text = stringResource(preset.labelRes),
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceMd)
                    ) {
                        CassyTextField(
                            value = state.primaryHex,
                            onValueChange = { onEvent(StoreSettingsUiEvent.UpdatePrimaryHex(it)) },
                            label = stringResource(R.string.cms_primary_hex_label),
                            isError = !state.isPrimaryHexValid,
                            supportingText = if (!state.isPrimaryHexValid) {
                                stringResource(R.string.cms_hex_error)
                            } else null,
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(parseHexColor(state.primaryHex, CassyTinta))
                                        .border(1.dp, colors.outline, CircleShape)
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                        CassyTextField(
                            value = state.accentHex,
                            onValueChange = { onEvent(StoreSettingsUiEvent.UpdateAccentHex(it)) },
                            label = stringResource(R.string.cms_accent_hex_label),
                            isError = !state.isAccentHexValid,
                            supportingText = if (!state.isAccentHexValid) {
                                stringResource(R.string.cms_hex_error)
                            } else null,
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(parseHexColor(state.accentHex, CassyKunyit))
                                        .border(1.dp, colors.outline, CircleShape)
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            CassyCard {
                Column(verticalArrangement = Arrangement.spacedBy(CassyDimens.SpaceMd)) {
                    CassySectionHeader(title = stringResource(R.string.cms_banner_card_title))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceMd),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (state.heroBannerUrl.isNotBlank() && state.isHeroBannerUrlValid) {
                            AsyncImage(
                                model = state.heroBannerUrl,
                                contentDescription = stringResource(R.string.cms_banner_preview_cd),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(width = 120.dp, height = 64.dp)
                                    .clip(RoundedCornerShape(CassyDimens.RadiusBadge))
                                    .border(1.dp, colors.outline, RoundedCornerShape(CassyDimens.RadiusBadge))
                            )
                        }

                        CassyTextField(
                            value = state.heroBannerUrl,
                            onValueChange = { onEvent(StoreSettingsUiEvent.UpdateHeroBannerUrl(it)) },
                            label = stringResource(R.string.cms_banner_url_label),
                            placeholder = stringResource(R.string.cms_banner_url_placeholder),
                            isError = !state.isHeroBannerUrlValid,
                            supportingText = if (!state.isHeroBannerUrlValid) {
                                stringResource(R.string.cms_banner_url_error)
                            } else null,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    HorizontalDivider(color = colors.outline)

                    val whiteLabelTitle = stringResource(R.string.cms_white_label_title)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = whiteLabelTitle,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = stringResource(R.string.cms_white_label_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = state.hidePoweredByBadge,
                            onCheckedChange = { onEvent(StoreSettingsUiEvent.ToggleWhiteLabel(it)) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colors.onPrimary,
                                checkedTrackColor = colors.primary
                            ),
                            modifier = Modifier
                                .padding(start = CassyDimens.SpaceSm)
                                .defaultMinSize(
                                    minWidth = CassyDimens.TouchTargetMin,
                                    minHeight = CassyDimens.TouchTargetMin
                                )
                                .semantics { contentDescription = whiteLabelTitle }
                        )
                    }
                }
            }

            CassyPrimaryButton(
                text = stringResource(
                    if (state.isSavingCms) R.string.cms_btn_saving else R.string.cms_btn_save
                ),
                onClick = { onEvent(StoreSettingsUiEvent.SaveStoreCms) },
                loading = state.isSavingCms,
                enabled = state.isHeroBannerUrlValid && state.isPrimaryHexValid && state.isAccentHexValid,
                icon = Icons.Rounded.Save,
                modifier = Modifier.fillMaxWidth()
            )
        }

        StoreBrandPreviewPanel(
            state = state,
            modifier = Modifier
                .weight(0.4f)
                .fillMaxWidth()
        )
    }
}
