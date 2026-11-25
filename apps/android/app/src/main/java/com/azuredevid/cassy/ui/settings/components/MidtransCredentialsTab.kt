package com.azuredevid.cassy.ui.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.VerifiedUser
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.azuredevid.cassy.R
import com.azuredevid.cassy.ui.designsystem.CassyCard
import com.azuredevid.cassy.ui.designsystem.CassyDimens
import com.azuredevid.cassy.ui.designsystem.CassyErrorBanner
import com.azuredevid.cassy.ui.designsystem.CassyPrimaryButton
import com.azuredevid.cassy.ui.designsystem.CassySecondaryButton
import com.azuredevid.cassy.ui.designsystem.CassySectionHeader
import com.azuredevid.cassy.ui.designsystem.CassyStatusBadge
import com.azuredevid.cassy.ui.designsystem.CassyTextActionButton
import com.azuredevid.cassy.ui.designsystem.CassyTextField
import com.azuredevid.cassy.ui.designsystem.CassyToastTone
import com.azuredevid.cassy.ui.settings.StoreSettingsUiEvent
import com.azuredevid.cassy.ui.settings.StoreSettingsUiState

/**
 * BYOK Midtrans credentials of the store.
 * Owner setting: moves to Cassy Manager (A3), see Docs/PRD/PrdFrontendUxAndLaunch.md section 4.
 */
@Composable
fun MidtransCredentialsTab(
    state: StoreSettingsUiState,
    onEvent: (StoreSettingsUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val colors = MaterialTheme.colorScheme

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(CassyDimens.SpaceXl)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(CassyDimens.SpaceLg)
    ) {
        CassyCard {
            val statusLabel = state.validationStatus.ifBlank {
                stringResource(R.string.midtrans_status_unverified)
            }
            CassySectionHeader(
                title = stringResource(R.string.midtrans_env_title),
                subtitle = stringResource(
                    if (state.isSandbox) {
                        R.string.midtrans_env_sandbox
                    } else {
                        R.string.midtrans_env_production
                    }
                ),
                trailing = {
                    CassyStatusBadge(
                        text = stringResource(
                            if (state.isSandbox) {
                                R.string.midtrans_badge_sandbox
                            } else {
                                R.string.midtrans_badge_production
                            }
                        ),
                        tone = if (state.isSandbox) CassyToastTone.WARNING else CassyToastTone.SUCCESS
                    )
                }
            )
            Spacer(Modifier.height(CassyDimens.SpaceSm))
            Text(
                text = stringResource(R.string.midtrans_status_badge, statusLabel),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant
            )
            state.lastVerifiedAt?.takeIf { it.isNotBlank() }?.let { timestamp ->
                Text(
                    text = stringResource(R.string.midtrans_last_verified, timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant
                )
            }
        }

        CassyCard {
            Column(verticalArrangement = Arrangement.spacedBy(CassyDimens.SpaceLg)) {
                val clientPrefix = if (state.isSandbox) "SB-Mid-client-" else "Mid-client-"
                val serverPrefix = if (state.isSandbox) "SB-Mid-server-" else "Mid-server-"

                val merchantInvalid = state.merchantId.isNotEmpty() && !state.isMerchantIdValid
                CassyTextField(
                    value = state.merchantId,
                    onValueChange = { onEvent(StoreSettingsUiEvent.UpdateMerchantId(it)) },
                    label = stringResource(R.string.midtrans_merchant_id_label),
                    placeholder = stringResource(R.string.midtrans_merchant_id_placeholder),
                    isError = merchantInvalid,
                    supportingText = stringResource(
                        if (merchantInvalid) R.string.midtrans_merchant_id_error else R.string.midtrans_merchant_id_help
                    )
                )

                val clientInvalid = state.clientKey.isNotEmpty() && !state.isClientKeyValid
                CassyTextField(
                    value = state.clientKey,
                    onValueChange = { onEvent(StoreSettingsUiEvent.UpdateClientKey(it)) },
                    label = stringResource(R.string.midtrans_client_key_label),
                    placeholder = "${clientPrefix}xxx",
                    isError = clientInvalid,
                    supportingText = if (clientInvalid) {
                        stringResource(R.string.midtrans_client_key_error, clientPrefix)
                    } else {
                        stringResource(R.string.midtrans_client_key_help)
                    }
                )

                val serverInvalid = state.serverKey.isNotEmpty() && !state.isServerKeyValid
                CassyTextField(
                    value = state.serverKey,
                    onValueChange = { onEvent(StoreSettingsUiEvent.UpdateServerKey(it)) },
                    label = stringResource(R.string.midtrans_server_key_label),
                    placeholder = state.serverKeyMasked.ifEmpty { "${serverPrefix}xxx" },
                    visualTransformation = if (state.isServerKeyVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        CassyTextActionButton(
                            text = stringResource(
                                if (state.isServerKeyVisible) R.string.midtrans_key_hide else R.string.midtrans_key_show
                            ),
                            onClick = { onEvent(StoreSettingsUiEvent.ToggleServerKeyVisibility) }
                        )
                    },
                    isError = serverInvalid,
                    supportingText = if (serverInvalid) {
                        stringResource(R.string.midtrans_server_key_error, serverPrefix)
                    } else {
                        stringResource(R.string.midtrans_server_key_help)
                    }
                )

                HorizontalDivider(color = colors.outline)

                val fallbackSwitchLabel = stringResource(R.string.midtrans_fallback_qris_title)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = fallbackSwitchLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(R.string.midtrans_fallback_qris_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = state.fallbackStaticQris,
                        onCheckedChange = { onEvent(StoreSettingsUiEvent.ToggleFallbackStaticQris(it)) },
                        colors = SwitchDefaults.colors(checkedThumbColor = colors.primary),
                        modifier = Modifier
                            .defaultMinSize(
                                minWidth = CassyDimens.TouchTargetMin,
                                minHeight = CassyDimens.TouchTargetMin
                            )
                            .semantics { contentDescription = fallbackSwitchLabel }
                    )
                }
            }
        }

        if (state.verificationMessage != null) {
            val tone = when (state.verificationStatus) {
                "success" -> CassyToastTone.SUCCESS
                "testing" -> CassyToastTone.INFO
                else -> CassyToastTone.ERROR
            }
            CassyErrorBanner(
                message = state.verificationMessage.asString(),
                tone = tone
            )
        }

        CassyCard(containerColor = colors.surfaceVariant.copy(alpha = 0.6f)) {
            CassySectionHeader(
                title = stringResource(R.string.midtrans_rolling_title),
                subtitle = stringResource(R.string.midtrans_rolling_body)
            )
        }

        val formReady = state.isMerchantIdValid && state.isClientKeyValid && state.isServerKeyValid
        val busy = state.isSavingMidtrans || state.isVerifyingMidtrans

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceLg)
        ) {
            CassySecondaryButton(
                text = stringResource(R.string.midtrans_btn_verify_live),
                onClick = { onEvent(StoreSettingsUiEvent.VerifyAndSaveMidtrans) },
                enabled = formReady && !busy,
                loading = state.isVerifyingMidtrans,
                icon = Icons.Rounded.VerifiedUser,
                modifier = Modifier.weight(1f)
            )
            CassyPrimaryButton(
                text = stringResource(R.string.midtrans_btn_save),
                onClick = { onEvent(StoreSettingsUiEvent.SaveMidtransCredentials) },
                enabled = formReady && !busy,
                loading = state.isSavingMidtrans,
                icon = Icons.Rounded.Save,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
