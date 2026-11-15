package com.azuredevid.cassy.ui.pairing.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.azuredevid.cassy.R
import com.azuredevid.cassy.ui.designsystem.CassyDimens
import com.azuredevid.cassy.ui.designsystem.CassyErrorBanner
import com.azuredevid.cassy.ui.designsystem.CassyPrimaryButton
import com.azuredevid.cassy.ui.designsystem.CassySecondaryButton
import com.azuredevid.cassy.ui.designsystem.CassyStatusBadge
import com.azuredevid.cassy.ui.designsystem.CassyTextActionButton
import com.azuredevid.cassy.ui.designsystem.CassyTextField
import com.azuredevid.cassy.ui.designsystem.CassyToastTone
import com.azuredevid.cassy.ui.pairing.StorePairingUiState
import com.azuredevid.cassy.ui.pairing.StorePairingViewModel
import com.azuredevid.cassy.ui.theme.CassyPandan
import com.azuredevid.cassy.ui.theme.CassyTinta
import com.azuredevid.cassy.ui.theme.LightSecondaryInk
import com.azuredevid.cassy.ui.theme.statusOkFg

@Composable
fun StorePairingFormPane(
    state: StorePairingUiState,
    onOpenQrScanner: () -> Unit,
    onOpenHidDialog: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onDismissCameraDenied: () -> Unit,
    onClearQrBadge: () -> Unit,
    onSlugChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPinChange: (String) -> Unit,
    onTogglePinVisibility: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val slugError = resolvePairingError(state.storeSlugError)
    val emailError = resolvePairingError(state.emailError)
    val pinError = resolvePairingError(state.pinError)
    val formError = resolvePairingError(state.formError)
    val qrInfo = if (state.qrScannedInfo == StorePairingViewModel.INFO_QR_SCANNED) {
        stringResource(R.string.auth_qr_scanned_hint)
    } else {
        state.qrScannedInfo
    }

    val emailFocus = remember { FocusRequester() }
    val pinFocus = remember { FocusRequester() }

    Column(
        modifier = modifier.widthIn(max = 560.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.pairing_screen_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = CassyTinta
        )
        Spacer(Modifier.height(CassyDimens.SpaceXs))
        Text(
            text = stringResource(R.string.pairing_screen_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = LightSecondaryInk
        )

        Spacer(Modifier.height(CassyDimens.SpaceLg))

        // Primary action: Fastest cashier onboarding flow via Camera QR Scan
        CassyPrimaryButton(
            text = stringResource(R.string.btn_scan_qr_camera),
            onClick = onOpenQrScanner,
            enabled = !state.isSubmitting,
            icon = Icons.Rounded.QrCodeScanner,
            height = 56.dp,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = CassyDimens.SpaceXs),
            horizontalArrangement = Arrangement.End
        ) {
            CassyTextActionButton(
                text = stringResource(R.string.pairing_btn_hid_scanner),
                onClick = onOpenHidDialog,
                enabled = !state.isSubmitting,
                icon = Icons.Rounded.Keyboard
            )
        }

        if (state.cameraPermissionDenied) {
            Spacer(Modifier.height(CassyDimens.SpaceSm))
            Surface(
                shape = RoundedCornerShape(CassyDimens.RadiusCard),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(CassyDimens.SpaceMd)) {
                    CassyErrorBanner(
                        message = "${stringResource(R.string.pairing_camera_permission_denied_title)}: " +
                            stringResource(R.string.pairing_camera_permission_denied_desc),
                        tone = CassyToastTone.WARNING
                    )
                    Spacer(Modifier.height(CassyDimens.SpaceSm))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceSm)
                    ) {
                        CassySecondaryButton(
                            text = stringResource(R.string.pairing_btn_open_app_settings),
                            onClick = onOpenAppSettings,
                            icon = Icons.Rounded.Settings,
                            modifier = Modifier.weight(1f)
                        )
                        CassyTextActionButton(
                            text = stringResource(R.string.shift_xreport_close_btn),
                            onClick = onDismissCameraDenied
                        )
                    }
                }
            }
        }

        if (!qrInfo.isNullOrBlank()) {
            Spacer(Modifier.height(CassyDimens.SpaceSm))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceSm)
            ) {
                CassyStatusBadge(
                    text = qrInfo,
                    tone = CassyToastTone.SUCCESS,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onClearQrBadge,
                    modifier = Modifier.size(CassyDimens.TouchTargetMin)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.a11y_clear_qr_badge),
                        tint = LightSecondaryInk
                    )
                }
            }
        }

        Spacer(Modifier.height(CassyDimens.SpaceMd))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outline)
            Text(
                text = stringResource(R.string.or_divider),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = CassyDimens.SpaceMd)
            )
            HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outline)
        }

        Spacer(Modifier.height(CassyDimens.SpaceMd))

        CassyTextField(
            value = state.storeSlug,
            onValueChange = onSlugChange,
            label = stringResource(R.string.store_slug_label),
            isError = slugError != null,
            supportingText = slugError,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Storefront,
                    contentDescription = null,
                    tint = LightSecondaryInk
                )
            },
            trailingIcon = if (slugError == null && state.isStoreSlugValid) {
                {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = statusOkFg(),
                        modifier = Modifier.size(CassyDimens.IconSizeMd)
                    )
                }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(onNext = { emailFocus.requestFocus() }),
            modifier = Modifier.defaultMinSize(minHeight = 56.dp)
        )

        Spacer(Modifier.height(CassyDimens.SpaceMd))

        CassyTextField(
            value = state.email,
            onValueChange = onEmailChange,
            label = stringResource(R.string.auth_email_label),
            isError = emailError != null,
            supportingText = emailError,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Email,
                    contentDescription = null,
                    tint = LightSecondaryInk
                )
            },
            trailingIcon = if (emailError == null && state.isEmailValid) {
                {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = statusOkFg(),
                        modifier = Modifier.size(CassyDimens.IconSizeMd)
                    )
                }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(onNext = { pinFocus.requestFocus() }),
            modifier = Modifier
                .defaultMinSize(minHeight = 56.dp)
                .focusRequester(emailFocus)
        )

        Spacer(Modifier.height(CassyDimens.SpaceMd))

        val pinHelper = pinError ?: if (state.isPinValid) {
            stringResource(R.string.auth_pin_ready_helper)
        } else {
            stringResource(R.string.auth_pin_helper, state.pin.length)
        }

        CassyTextField(
            value = state.pin,
            onValueChange = onPinChange,
            label = stringResource(R.string.auth_pin_label),
            visualTransformation = if (state.isPinVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            isError = pinError != null,
            supportingText = pinHelper,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = LightSecondaryInk
                )
            },
            trailingIcon = {
                IconButton(
                    onClick = onTogglePinVisibility,
                    modifier = Modifier.size(CassyDimens.TouchTargetMin)
                ) {
                    Icon(
                        imageVector = if (state.isPinVisible) {
                            Icons.Rounded.VisibilityOff
                        } else {
                            Icons.Rounded.Visibility
                        },
                        contentDescription = stringResource(R.string.a11y_toggle_pin_visibility),
                        tint = LightSecondaryInk
                    )
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            modifier = Modifier
                .defaultMinSize(minHeight = 56.dp)
                .focusRequester(pinFocus)
        )

        if (!formError.isNullOrBlank()) {
            Spacer(Modifier.height(CassyDimens.SpaceMd))
            CassyErrorBanner(message = formError, tone = CassyToastTone.ERROR)
        }

        Spacer(Modifier.height(CassyDimens.SpaceLg))

        // Secondary tonal-outlined action: deliberately subordinate to Primary QR Scan CTA
        OutlinedButton(
            onClick = onSubmit,
            enabled = !state.isSubmitting,
            shape = RoundedCornerShape(CassyDimens.RadiusButton),
            border = BorderStroke(1.5.dp, CassyPandan),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = CassyPandan.copy(alpha = 0.08f),
                contentColor = CassyPandan
            ),
            contentPadding = PaddingValues(horizontal = CassyDimens.SpaceLg, vertical = CassyDimens.SpaceSm),
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minWidth = CassyDimens.TouchTargetMin, minHeight = 56.dp)
        ) {
            if (state.isSubmitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(CassyDimens.IconSizeSm),
                    strokeWidth = 2.dp,
                    color = CassyPandan
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Login,
                    contentDescription = null,
                    modifier = Modifier.size(CassyDimens.IconSizeSm)
                )
            }
            Spacer(Modifier.width(CassyDimens.SpaceSm))
            Text(
                text = stringResource(R.string.auth_btn_sign_in),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun resolvePairingError(codeOrMessage: String?): String? {
    if (codeOrMessage.isNullOrBlank()) return null
    return when (codeOrMessage) {
        StorePairingViewModel.ERR_QR_EMPTY -> stringResource(R.string.pairing_err_qr_empty)
        StorePairingViewModel.ERR_QR_FORMAT -> stringResource(R.string.pairing_err_qr_format)
        StorePairingViewModel.ERR_SLUG_REQUIRED -> stringResource(R.string.auth_err_store_slug_required)
        StorePairingViewModel.ERR_SLUG_INVALID -> stringResource(R.string.auth_err_store_slug_invalid)
        StorePairingViewModel.ERR_EMAIL_REQUIRED -> stringResource(R.string.auth_err_email_required)
        StorePairingViewModel.ERR_EMAIL_INVALID -> stringResource(R.string.auth_err_email_invalid)
        StorePairingViewModel.ERR_PIN_INVALID -> stringResource(R.string.auth_err_pin_six_digits)
        StorePairingViewModel.ERR_AUTH_FAILED -> stringResource(R.string.pairing_err_auth_failed)
        else -> codeOrMessage
    }
}
