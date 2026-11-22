package com.azuredevid.cassy.ui.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.azuredevid.cassy.R
import com.azuredevid.cassy.ui.designsystem.CassyCard
import com.azuredevid.cassy.ui.designsystem.CassyDimens
import com.azuredevid.cassy.ui.designsystem.CassySectionHeader
import com.azuredevid.cassy.ui.settings.StoreSettingsUiEvent
import com.azuredevid.cassy.ui.settings.StoreSettingsUiState

private data class FeatureFlagItem(
    val key: String,
    val titleRes: Int,
    val descRes: Int
)

private val GUEST_V4_FEATURE_FLAGS = listOf(
    FeatureFlagItem("guest_self_cancel_enabled", R.string.feature_guest_self_cancel_title, R.string.feature_guest_self_cancel_desc),
    FeatureFlagItem("guest_split_equal_enabled", R.string.feature_guest_split_equal_title, R.string.feature_guest_split_equal_desc),
    FeatureFlagItem("guest_split_by_item_enabled", R.string.feature_guest_split_by_item_title, R.string.feature_guest_split_by_item_desc),
    FeatureFlagItem("guest_split_multi_payment_enabled", R.string.feature_guest_split_multi_title, R.string.feature_guest_split_multi_desc),
    FeatureFlagItem("guest_bell_sound_enabled", R.string.feature_guest_bell_title, R.string.feature_guest_bell_desc),
    FeatureFlagItem("cart_pairing_enabled", R.string.feature_cart_pairing_title, R.string.feature_cart_pairing_desc),
    FeatureFlagItem("guest_email_receipt_enabled", R.string.feature_guest_email_receipt_title, R.string.feature_guest_email_receipt_desc)
)

@Composable
fun StoreFeatureFlagsTab(
    state: StoreSettingsUiState,
    onEvent: (StoreSettingsUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val colors = MaterialTheme.colorScheme

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = CassyDimens.SpaceXl, vertical = CassyDimens.SpaceLg),
        verticalArrangement = Arrangement.spacedBy(CassyDimens.SpaceLg)
    ) {
        CassyCard {
            CassySectionHeader(
                title = stringResource(R.string.features_section_title),
                subtitle = stringResource(R.string.features_section_desc)
            )
        }

        CassyCard {
            Column(verticalArrangement = Arrangement.spacedBy(CassyDimens.SpaceSm)) {
                GUEST_V4_FEATURE_FLAGS.forEachIndexed { index, item ->
                    val title = stringResource(item.titleRes)
                    val checked = state.featureFlags[item.key] ?: true
                    val busy = state.updatingFeatureKey == item.key
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = CassyDimens.TouchTargetMin)
                            .padding(vertical = CassyDimens.SpaceXs),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = CassyDimens.SpaceMd)
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.onSurface
                            )
                            Text(
                                text = stringResource(item.descRes),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = checked,
                            enabled = !busy,
                            onCheckedChange = { onEvent(StoreSettingsUiEvent.ToggleFeatureFlag(item.key, it)) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colors.onPrimary,
                                checkedTrackColor = colors.primary
                            ),
                            modifier = Modifier.semantics { contentDescription = title }
                        )
                    }
                    if (index < GUEST_V4_FEATURE_FLAGS.lastIndex) {
                        HorizontalDivider(color = colors.outlineVariant)
                    }
                }
            }
        }
    }
}
