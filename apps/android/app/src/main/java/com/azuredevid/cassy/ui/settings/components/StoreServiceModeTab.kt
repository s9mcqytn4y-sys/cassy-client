package com.azuredevid.cassy.ui.settings.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.azuredevid.cassy.R
import com.azuredevid.cassy.domain.model.QRServiceMode
import com.azuredevid.cassy.ui.designsystem.CassyCard
import com.azuredevid.cassy.ui.designsystem.CassyDimens
import com.azuredevid.cassy.ui.designsystem.CassyErrorBanner
import com.azuredevid.cassy.ui.designsystem.CassyPrimaryButton
import com.azuredevid.cassy.ui.designsystem.CassySecondaryButton
import com.azuredevid.cassy.ui.designsystem.CassySectionHeader
import com.azuredevid.cassy.ui.designsystem.CassyToastTone
import com.azuredevid.cassy.ui.settings.StoreSettingsUiEvent
import com.azuredevid.cassy.ui.settings.StoreSettingsUiState
import com.azuredevid.cassy.ui.theme.statusWarnBg
import com.azuredevid.cassy.ui.theme.statusWarnFg

/**
 * QR service mode, table timer and enabled payment methods.
 * Owner setting: moves to Cassy Manager (A3), see Docs/PRD/PrdFrontendUxAndLaunch.md section 4.
 */
@Composable
fun StoreServiceModeTab(
    state: StoreSettingsUiState,
    onEvent: (StoreSettingsUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val colors = MaterialTheme.colorScheme
    val isMidtransVerified = state.validationStatus.equals("valid", ignoreCase = true)

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = CassyDimens.SpaceXl, vertical = CassyDimens.SpaceLg)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(CassyDimens.SpaceLg)
        ) {
        CassyCard {
            CassySectionHeader(
                title = stringResource(R.string.service_section_title),
                subtitle = stringResource(R.string.service_section_desc)
            )
        }

        val audioTitle = stringResource(R.string.service_audio_mute_title)
        CassyCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = CassyDimens.TouchTargetMin),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = CassyDimens.SpaceMd)) {
                    Text(
                        text = audioTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onSurface
                    )
                    Text(
                        text = stringResource(R.string.service_audio_mute_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }
                Switch(
                    checked = state.audioCueMuted,
                    onCheckedChange = { onEvent(StoreSettingsUiEvent.ToggleAudioCueMuted(it)) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colors.onPrimary,
                        checkedTrackColor = colors.primary
                    ),
                    modifier = Modifier.semantics { contentDescription = audioTitle }
                )
            }
        }

        if (!state.hasManageAccess) {
            CassyErrorBanner(
                message = stringResource(R.string.service_readonly_owner_hint),
                tone = CassyToastTone.INFO
            )
        } else {
        CassyCard {
            Column(verticalArrangement = Arrangement.spacedBy(CassyDimens.SpaceMd)) {
                CassySectionHeader(
                    title = stringResource(R.string.service_qr_mode_title),
                    subtitle = stringResource(R.string.service_qr_mode_desc)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceMd)
                ) {
                    ServiceModeOptionCard(
                        title = stringResource(R.string.service_mode_table_title),
                        description = stringResource(R.string.service_mode_table_desc),
                        selected = state.qrServiceMode == QRServiceMode.TABLE,
                        onSelect = { onEvent(StoreSettingsUiEvent.SetServiceMode(QRServiceMode.TABLE)) },
                        modifier = Modifier.weight(1f)
                    )
                    ServiceModeOptionCard(
                        title = stringResource(R.string.service_mode_pickup_title),
                        description = stringResource(R.string.service_mode_pickup_desc),
                        selected = state.qrServiceMode == QRServiceMode.PICKUP,
                        onSelect = { onEvent(StoreSettingsUiEvent.SetServiceMode(QRServiceMode.PICKUP)) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        val pauseTitle = stringResource(R.string.service_pause_title)
        CassyCard(
            containerColor = if (state.qrOrderingPaused) statusWarnBg() else colors.surface,
            borderColor = if (state.qrOrderingPaused) statusWarnFg() else colors.outline
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = pauseTitle,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (state.qrOrderingPaused) statusWarnFg() else colors.onSurface
                    )
                    Text(
                        text = stringResource(
                            if (state.qrOrderingPaused) {
                                R.string.service_pause_active_desc
                            } else {
                                R.string.service_pause_idle_desc
                            }
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.qrOrderingPaused) statusWarnFg() else colors.onSurfaceVariant
                    )
                }

                Switch(
                    checked = state.qrOrderingPaused,
                    onCheckedChange = { onEvent(StoreSettingsUiEvent.ToggleEmergencyQrPause(it)) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colors.onPrimary,
                        checkedTrackColor = statusWarnFg()
                    ),
                    modifier = Modifier
                        .padding(start = CassyDimens.SpaceSm)
                        .defaultMinSize(
                            minWidth = CassyDimens.TouchTargetMin,
                            minHeight = CassyDimens.TouchTargetMin
                        )
                        .semantics { contentDescription = pauseTitle }
                )
            }
        }

        CassyCard {
            Column(verticalArrangement = Arrangement.spacedBy(CassyDimens.SpaceMd)) {
                CassySectionHeader(title = stringResource(R.string.service_table_limit_title))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.service_table_limit_label),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (state.tableTimeLimitMinutes == 0) {
                                stringResource(R.string.service_table_limit_unlimited)
                            } else {
                                stringResource(
                                    R.string.service_table_limit_minutes,
                                    state.tableTimeLimitMinutes
                                )
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceSm)
                    ) {
                        CassySecondaryButton(
                            text = "-15",
                            onClick = {
                                val newLimit = (state.tableTimeLimitMinutes - 15).coerceAtLeast(0)
                                onEvent(StoreSettingsUiEvent.UpdateTableTimeLimit(newLimit))
                            },
                            enabled = state.tableTimeLimitMinutes > 0
                        )
                        Text(
                            text = stringResource(R.string.service_table_limit_chip, state.tableTimeLimitMinutes),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(68.dp),
                            textAlign = TextAlign.Center
                        )
                        CassySecondaryButton(
                            text = "+15",
                            onClick = {
                                val newLimit = (state.tableTimeLimitMinutes + 15).coerceAtMost(240)
                                onEvent(StoreSettingsUiEvent.UpdateTableTimeLimit(newLimit))
                            },
                            enabled = state.tableTimeLimitMinutes < 240
                        )
                    }
                }

                HorizontalDivider(color = colors.outline)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.service_table_warning_label),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(
                                R.string.service_table_warning_desc,
                                state.tableTimerWarningMinutes
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                    Text(
                        text = stringResource(
                            R.string.service_table_warning_value,
                            state.tableTimerWarningMinutes
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        RemotePaymentMethodsCard(
            enabledMethods = state.enabledPaymentMethods,
            isMidtransVerified = isMidtransVerified,
            onToggle = { method, enabled ->
                onEvent(StoreSettingsUiEvent.ToggleRemotePaymentMethod(method, enabled))
            }
        )
        }
        }

        if (state.hasManageAccess) {
        Surface(
            color = colors.surface,
            tonalElevation = 2.dp,
            border = BorderStroke(1.dp, colors.outlineVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CassyDimens.SpaceXl, vertical = CassyDimens.SpaceMd)
            ) {
                CassyPrimaryButton(
                    text = stringResource(
                        if (state.isSavingSettings) R.string.service_btn_saving else R.string.service_btn_save
                    ),
                    onClick = { onEvent(StoreSettingsUiEvent.SaveStoreSettings) },
                    loading = state.isSavingSettings,
                    icon = Icons.Rounded.Save,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        }
    }
}

@Composable
private fun RemotePaymentMethodsCard(
    enabledMethods: List<String>,
    isMidtransVerified: Boolean,
    onToggle: (String, Boolean) -> Unit
) {
    val channels = listOf(
        "qris" to R.string.service_pay_qris,
        "gopay" to R.string.service_pay_gopay,
        "shopeepay" to R.string.service_pay_shopeepay,
        "bank_transfer" to R.string.service_pay_bank
    )
    CassyCard {
        Column(verticalArrangement = Arrangement.spacedBy(CassyDimens.SpaceMd)) {
            CassySectionHeader(
                title = stringResource(R.string.service_remote_pay_title),
                subtitle = stringResource(R.string.service_remote_pay_desc)
            )
            if (!isMidtransVerified) {
                CassyErrorBanner(
                    message = stringResource(R.string.service_remote_pay_unverified),
                    tone = CassyToastTone.WARNING
                )
            }
            channels.forEach { (key, labelRes) ->
                val checked = enabledMethods.contains(key)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(labelRes),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Switch(
                        checked = checked,
                        enabled = isMidtransVerified || checked,
                        onCheckedChange = { onToggle(key, it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ServiceModeOptionCard(
    title: String,
    description: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .defaultMinSize(minHeight = CassyDimens.TouchTargetMin)
            .clip(RoundedCornerShape(CassyDimens.RadiusCard))
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onSelect
            ),
        shape = RoundedCornerShape(CassyDimens.RadiusCard),
        color = if (selected) colors.surfaceVariant else colors.surface,
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) colors.primary else colors.outline
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(CassyDimens.SpaceLg),
            verticalArrangement = Arrangement.spacedBy(CassyDimens.SpaceSm)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(if (selected) colors.primary else Color.Transparent)
                        .border(2.dp, if (selected) colors.primary else colors.outline, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (selected) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(colors.onPrimary)
                        )
                    }
                }
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
        }
    }
}
