package com.azuredevid.cassy.ui.shift.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.azuredevid.cassy.R
import com.azuredevid.cassy.domain.model.CashMovementType
import com.azuredevid.cassy.ui.designsystem.CassyCard
import com.azuredevid.cassy.ui.designsystem.CassyDimens
import com.azuredevid.cassy.ui.designsystem.CassyErrorBanner
import com.azuredevid.cassy.ui.designsystem.CassyModalDialog
import com.azuredevid.cassy.ui.designsystem.CassyPrimaryButton
import com.azuredevid.cassy.ui.designsystem.CassySecondaryButton
import com.azuredevid.cassy.ui.designsystem.CassyTextField
import com.azuredevid.cassy.ui.designsystem.CassyToastTone
import com.azuredevid.cassy.ui.theme.statusWarnBg
import com.azuredevid.cassy.ui.theme.statusWarnFg
import java.text.NumberFormat
import java.util.Locale

@Composable
fun CashMovementModal(
    movementType: CashMovementType,
    amountInput: String,
    reason: String,
    isRecording: Boolean,
    errorMessage: String? = null,
    onMovementTypeChanged: (CashMovementType) -> Unit,
    onAmountChanged: (String) -> Unit,
    onReasonChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rupiahFormatter = remember { NumberFormat.getIntegerInstance(Locale("id", "ID")) }
    val currentAmount = amountInput.toLongOrNull() ?: 0L
    val presets = remember { listOf(10_000L, 20_000L, 50_000L, 100_000L) }
    val warnFg = statusWarnFg()

    CassyModalDialog(
        title = stringResource(R.string.shift_movement_title),
        subtitle = stringResource(R.string.shift_movement_subtitle),
        icon = Icons.Rounded.SwapHoriz,
        maxWidth = CassyDimens.ModalWidthStandard,
        dismissEnabled = !isRecording,
        onDismissRequest = onDismiss,
        modifier = modifier,
        footer = {
            CassySecondaryButton(
                text = stringResource(R.string.btn_cancel),
                onClick = onDismiss,
                enabled = !isRecording,
                modifier = Modifier.weight(1f)
            )
            CassyPrimaryButton(
                text = stringResource(R.string.shift_btn_save_movement),
                onClick = onSubmit,
                loading = isRecording,
                modifier = Modifier.weight(1f)
            )
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceSm)
        ) {
            val isPaidOut = movementType == CashMovementType.PAID_OUT
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = CassyDimens.TouchTargetMin)
                    .selectable(
                        selected = isPaidOut,
                        role = Role.RadioButton,
                        onClick = { onMovementTypeChanged(CashMovementType.PAID_OUT) }
                    ),
                shape = RoundedCornerShape(CassyDimens.RadiusButton),
                color = if (isPaidOut) statusWarnBg() else MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    width = if (isPaidOut) 1.5.dp else 1.dp,
                    color = if (isPaidOut) warnFg else MaterialTheme.colorScheme.outline
                )
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.shift_movement_paid_out),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isPaidOut) FontWeight.Bold else FontWeight.Medium,
                        color = if (isPaidOut) warnFg else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            val isCashIn = movementType == CashMovementType.CASH_IN
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = CassyDimens.TouchTargetMin)
                    .selectable(
                        selected = isCashIn,
                        role = Role.RadioButton,
                        onClick = { onMovementTypeChanged(CashMovementType.CASH_IN) }
                    ),
                shape = RoundedCornerShape(CassyDimens.RadiusButton),
                color = if (isCashIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                border = if (isCashIn) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.shift_movement_cash_in),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isCashIn) FontWeight.Bold else FontWeight.Medium,
                        color = if (isCashIn) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }
        }

        CassyCard(
            containerColor = MaterialTheme.colorScheme.background,
            contentPadding = CassyDimens.SpaceLg
        ) {
            Text(
                text = stringResource(R.string.shift_movement_amount_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.cash_chip_rp, rupiahFormatter.format(currentAmount)),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = if (movementType.isDeduction) warnFg else MaterialTheme.colorScheme.primary
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceSm)
        ) {
            presets.forEach { preset ->
                val isSelected = currentAmount == preset
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = CassyDimens.TouchTargetMin)
                        .selectable(
                            selected = isSelected,
                            role = Role.RadioButton,
                            onClick = { onAmountChanged(preset.toString()) }
                        ),
                    shape = RoundedCornerShape(CassyDimens.RadiusButton),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.shift_preset_rb, (preset / 1000L).toInt()),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }
            }
        }

        CassyTextField(
            value = amountInput,
            onValueChange = onAmountChanged,
            label = stringResource(R.string.shift_movement_custom_amount),
            placeholder = "0",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        CassyTextField(
            value = reason,
            onValueChange = onReasonChanged,
            label = stringResource(R.string.shift_movement_reason_label),
            placeholder = stringResource(R.string.shift_movement_reason_placeholder)
        )

        if (!errorMessage.isNullOrBlank()) {
            CassyErrorBanner(message = errorMessage, tone = CassyToastTone.ERROR)
        }
    }
}
