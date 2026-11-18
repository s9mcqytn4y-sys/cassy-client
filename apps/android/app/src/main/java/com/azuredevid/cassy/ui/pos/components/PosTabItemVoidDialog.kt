package com.azuredevid.cassy.ui.pos.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.azuredevid.cassy.R
import com.azuredevid.cassy.ui.designsystem.CassyDangerButton
import com.azuredevid.cassy.ui.designsystem.CassyDimens
import com.azuredevid.cassy.ui.designsystem.CassyModalDialog
import com.azuredevid.cassy.ui.designsystem.CassySecondaryButton
import com.azuredevid.cassy.ui.designsystem.CassyTextField
import com.azuredevid.cassy.ui.designsystem.CassyTonalButton
import com.azuredevid.cassy.ui.pos.TabItemVoidUiState
import com.azuredevid.cassy.ui.pos.TabVoidStep
import com.azuredevid.cassy.ui.theme.statusWarnFg

/** Void of one sent tab line: cashier PIN first, owner PIN or a remote owner approval above the limit. */
@Composable
fun PosTabItemVoidDialog(
    state: TabItemVoidUiState,
    onQuantityChange: (Int) -> Unit,
    onReasonChange: (String) -> Unit,
    onPinChange: (String) -> Unit,
    onOwnerPinChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onAskOwner: () -> Unit,
    onDismiss: () -> Unit
) {
    val waiting = state.step == TabVoidStep.WAITING
    val editable = !waiting && !state.isBusy
    val pinKeyboard = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
    CassyModalDialog(
        title = stringResource(R.string.tab_void_title),
        subtitle = stringResource(R.string.tab_void_subtitle, state.itemName, state.maxQuantity),
        icon = Icons.Rounded.RemoveCircleOutline,
        maxWidth = CassyDimens.ModalWidthStandard,
        dismissEnabled = !state.isBusy,
        onDismissRequest = onDismiss,
        footer = {
            CassySecondaryButton(
                text = stringResource(R.string.tab_void_btn_close),
                onClick = onDismiss,
                enabled = !state.isBusy,
                modifier = Modifier.weight(1f)
            )
            if (!waiting) {
                CassyDangerButton(
                    text = stringResource(R.string.tab_void_btn_submit),
                    onClick = onSubmit,
                    enabled = state.canSubmit,
                    loading = state.isBusy,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    ) {
        // First in the body so a phone in landscape shows it without scrolling.
        if (waiting) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceSm)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(CassyDimens.IconSizeSm), strokeWidth = 2.dp)
                Text(text = stringResource(R.string.tab_void_waiting), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceSm)
        ) {
            Text(
                text = stringResource(R.string.tab_void_quantity),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            StepButton(
                icon = Icons.Rounded.Remove,
                description = stringResource(R.string.a11y_decrease_qty, state.itemName),
                enabled = editable && state.quantity > 1,
                onClick = { onQuantityChange(state.quantity - 1) }
            )
            Text(
                text = "${state.quantity}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 28.dp)
            )
            StepButton(
                icon = Icons.Rounded.Add,
                description = stringResource(R.string.a11y_increase_qty, state.itemName),
                enabled = editable && state.quantity < state.maxQuantity,
                onClick = { onQuantityChange(state.quantity + 1) }
            )
        }
        CassyTextField(
            value = state.reason,
            onValueChange = onReasonChange,
            label = stringResource(R.string.tab_void_reason),
            placeholder = stringResource(R.string.tab_void_reason_hint),
            enabled = editable
        )
        CassyTextField(
            value = state.pin,
            onValueChange = onPinChange,
            label = stringResource(R.string.tab_void_pin),
            enabled = editable,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = pinKeyboard
        )
        if (state.step == TabVoidStep.OWNER) {
            Text(
                text = stringResource(R.string.tab_void_owner_needed),
                style = MaterialTheme.typography.bodyMedium,
                color = statusWarnFg()
            )
            CassyTextField(
                value = state.ownerPin,
                onValueChange = onOwnerPinChange,
                label = stringResource(R.string.tab_void_owner_pin),
                enabled = editable,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = pinKeyboard
            )
            CassyTonalButton(
                text = stringResource(R.string.tab_void_btn_ask_owner),
                onClick = onAskOwner,
                enabled = state.canAskOwner,
                modifier = Modifier.fillMaxWidth()
            )
        }
        state.error?.let {
            Spacer(Modifier.size(CassyDimens.SpaceXs))
            Text(text = it.asString(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun StepButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(CassyDimens.RadiusButton),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier
            .size(CassyDimens.TouchTargetMin)
            .semantics { contentDescription = description }
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
    }
}
