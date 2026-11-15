package com.azuredevid.cassy.ui.pairing.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.KeyboardHide
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.azuredevid.cassy.R
import com.azuredevid.cassy.ui.designsystem.CassyDimens
import com.azuredevid.cassy.ui.designsystem.CassyModalDialog
import com.azuredevid.cassy.ui.designsystem.CassyPrimaryButton
import com.azuredevid.cassy.ui.designsystem.CassySecondaryButton
import com.azuredevid.cassy.ui.theme.BrandStatusCheckingDot
import com.azuredevid.cassy.ui.theme.BrandStatusOnlineDot
import com.azuredevid.cassy.ui.theme.CassyPandan
import com.azuredevid.cassy.ui.theme.LightSecondaryInk
import com.azuredevid.cassy.ui.theme.statusOkFg
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun StorePairingHidDialog(
    payloadInput: String,
    errorCodeOrMessage: String? = null,
    onPayloadChange: (String) -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val clipboardManager = LocalClipboardManager.current
    val windowInfo = LocalWindowInfo.current
    var isFieldFocused by remember { mutableStateOf(false) }
    var isSoftKeyboardVisible by remember { mutableStateOf(false) }
    var showEmptyClipboardHint by remember { mutableStateOf(false) }

    val resolvedError = resolvePairingError(errorCodeOrMessage)
    val emptyClipboardHint = stringResource(R.string.pairing_hid_clipboard_empty)

    val scannerPulseTransition = rememberInfiniteTransition(label = "hidScannerPulse")
    val pulseAlpha by scannerPulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850),
            repeatMode = RepeatMode.Reverse
        ),
        label = "hidScannerPulseAlpha"
    )

    LaunchedEffect(focusRequester) {
        // Wait briefly for Dialog OS window focus, then request focus regardless and retry once if needed.
        withTimeoutOrNull(400L) {
            snapshotFlow { windowInfo.isWindowFocused }.first { it }
        }
        withFrameNanos { }
        runCatching { focusRequester.requestFocus() }
        keyboardController?.hide()
        delay(150L)
        if (!isFieldFocused) {
            runCatching { focusRequester.requestFocus() }
            keyboardController?.hide()
        }
    }

    CassyModalDialog(
        title = stringResource(R.string.pairing_hid_dialog_title),
        subtitle = stringResource(R.string.pairing_hid_dialog_desc),
        icon = Icons.Rounded.QrCodeScanner,
        onDismissRequest = onDismiss,
        footer = {
            CassySecondaryButton(
                text = stringResource(R.string.btn_cancel),
                onClick = onDismiss
            )
            CassyPrimaryButton(
                text = stringResource(R.string.pairing_hid_apply),
                onClick = onApply,
                enabled = payloadInput.isNotBlank(),
                icon = Icons.Rounded.QrCodeScanner
            )
        }
    ) {
        Spacer(Modifier.height(CassyDimens.SpaceXs))

        val supportingMessage = resolvedError ?: if (showEmptyClipboardHint) emptyClipboardHint else null
        val isErrorState = resolvedError != null

        Column(verticalArrangement = Arrangement.spacedBy(CassyDimens.SpaceSm)) {
            OutlinedTextField(
                value = payloadInput,
                onValueChange = { next ->
                    showEmptyClipboardHint = false
                    if (next.endsWith('\n') || next.endsWith('\r')) {
                        onPayloadChange(next.trimEnd('\r', '\n'))
                        onApply()
                    } else {
                        onPayloadChange(next)
                    }
                },
                label = { Text(stringResource(R.string.pairing_hid_field_label)) },
                isError = isErrorState,
                supportingText = supportingMessage?.let { msg -> { Text(msg) } },
                singleLine = false,
                minLines = 1,
                maxLines = 3,
                shape = RoundedCornerShape(CassyDimens.RadiusButton),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CassyPandan,
                    focusedLabelColor = CassyPandan,
                    unfocusedBorderColor = if (isFieldFocused) CassyPandan else MaterialTheme.colorScheme.outline,
                    errorBorderColor = MaterialTheme.colorScheme.error,
                    errorLabelColor = MaterialTheme.colorScheme.error,
                    cursorColor = CassyPandan
                ),
                trailingIcon = {
                    Row(
                        modifier = Modifier.padding(end = CassyDimens.SpaceXs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                val nextShow = !isSoftKeyboardVisible
                                isSoftKeyboardVisible = nextShow
                                runCatching { focusRequester.requestFocus() }
                                if (nextShow) {
                                    keyboardController?.show()
                                } else {
                                    keyboardController?.hide()
                                }
                            },
                            modifier = Modifier.size(CassyDimens.TouchTargetMin)
                        ) {
                            Icon(
                                imageVector = if (isSoftKeyboardVisible) {
                                    Icons.Rounded.KeyboardHide
                                } else {
                                    Icons.Rounded.Keyboard
                                },
                                contentDescription = stringResource(
                                    if (isSoftKeyboardVisible) {
                                        R.string.pairing_hid_keyboard_hide_cd
                                    } else {
                                        R.string.pairing_hid_keyboard_show_cd
                                    }
                                ),
                                tint = if (isSoftKeyboardVisible) CassyPandan else LightSecondaryInk
                            )
                        }
                        IconButton(
                            onClick = {
                                val pasted = clipboardManager.getText()?.text.orEmpty().trim()
                                if (pasted.isBlank()) {
                                    showEmptyClipboardHint = true
                                } else {
                                    showEmptyClipboardHint = false
                                    onPayloadChange(pasted)
                                    runCatching { focusRequester.requestFocus() }
                                }
                            },
                            modifier = Modifier.size(CassyDimens.TouchTargetMin)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ContentPaste,
                                contentDescription = stringResource(R.string.pairing_hid_paste_cd),
                                tint = CassyPandan
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onApply() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { focusState ->
                        isFieldFocused = focusState.isFocused
                    }
                    .onPreviewKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown &&
                            (keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter)
                        ) {
                            onApply()
                            true
                        } else {
                            false
                        }
                    }
            )

            // Live listener badge tied to real input focus rather than a decorative label.
            Surface(
                shape = RoundedCornerShape(CassyDimens.RadiusBadge),
                color = if (isFieldFocused) {
                    CassyPandan.copy(alpha = 0.08f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                border = BorderStroke(
                    1.dp,
                    if (isFieldFocused) CassyPandan.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(CassyDimens.RadiusBadge))
                    .clickable(enabled = !isFieldFocused, role = Role.Button) {
                        runCatching { focusRequester.requestFocus() }
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = CassyDimens.SpaceMd, vertical = CassyDimens.SpaceXs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceSm)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .alpha(if (isFieldFocused) pulseAlpha else 1f)
                            .clip(CircleShape)
                            .background(if (isFieldFocused) BrandStatusOnlineDot else BrandStatusCheckingDot)
                    )
                    Icon(
                        imageVector = Icons.Rounded.QrCodeScanner,
                        contentDescription = null,
                        tint = if (isFieldFocused) statusOkFg() else LightSecondaryInk,
                        modifier = Modifier
                            .size(CassyDimens.IconSizeSm)
                            .alpha(if (isFieldFocused) pulseAlpha else 0.75f)
                    )
                    Text(
                        text = stringResource(
                            if (isFieldFocused) R.string.pairing_hid_ready_scan else R.string.pairing_hid_tap_focus
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (isFieldFocused) statusOkFg() else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
