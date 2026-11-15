package com.azuredevid.cassy.ui.pairing.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import com.azuredevid.cassy.R
import com.azuredevid.cassy.ui.designsystem.CassyDimens
import com.azuredevid.cassy.ui.designsystem.CassyModalDialog
import com.azuredevid.cassy.ui.designsystem.CassyPrimaryButton
import com.azuredevid.cassy.ui.designsystem.CassySecondaryButton
import com.azuredevid.cassy.ui.designsystem.CassyTextField

@Composable
fun StorePairingHidDialog(
    payloadInput: String,
    onPayloadChange: (String) -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        // Wait for the Dialog subcomposition window to attach FocusRequester before requesting focus.
        withFrameNanos { }
        withFrameNanos { }
        runCatching { focusRequester.requestFocus() }
    }

    CassyModalDialog(
        title = stringResource(R.string.pairing_hid_dialog_title),
        subtitle = stringResource(R.string.pairing_hid_dialog_desc),
        icon = Icons.Rounded.Keyboard,
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
        CassyTextField(
            value = payloadInput,
            onValueChange = { next ->
                if (next.endsWith('\n')) {
                    onPayloadChange(next.trimEnd('\r', '\n'))
                    onApply()
                } else {
                    onPayloadChange(next)
                }
            },
            label = stringResource(R.string.pairing_hid_field_label),
            singleLine = false,
            minLines = 3,
            maxLines = 5,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onApply() }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
        )
    }
}
