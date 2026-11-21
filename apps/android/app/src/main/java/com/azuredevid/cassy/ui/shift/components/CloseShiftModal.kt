package com.azuredevid.cassy.ui.shift.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.azuredevid.cassy.R
import com.azuredevid.cassy.core.util.ThousandsSeparatorTransformation
import com.azuredevid.cassy.domain.model.Shift
import com.azuredevid.cassy.ui.designsystem.CassyDimens
import com.azuredevid.cassy.ui.designsystem.CassyErrorBanner
import com.azuredevid.cassy.ui.designsystem.CassyModalDialog
import com.azuredevid.cassy.ui.designsystem.CassyPrimaryButton
import com.azuredevid.cassy.ui.designsystem.CassySecondaryButton
import com.azuredevid.cassy.ui.designsystem.CassyTextField
import com.azuredevid.cassy.ui.designsystem.CassyToastTone
import com.azuredevid.cassy.ui.theme.statusOkBg
import com.azuredevid.cassy.ui.theme.statusOkFg
import com.azuredevid.cassy.ui.theme.statusWarnBg
import com.azuredevid.cassy.ui.theme.statusWarnFg
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

@Composable
fun CloseShiftModal(
    currentShift: Shift?,
    countedCashInput: String,
    varianceReason: String,
    notes: String,
    supervisorPinInput: String,
    calculatedVariance: Long?,
    isSupervisorAuthRequired: Boolean,
    printReceiptOnClose: Boolean,
    isClosing: Boolean,
    errorMessage: String? = null,
    onCountedCashChanged: (String) -> Unit,
    onVarianceReasonChanged: (String) -> Unit,
    onNotesChanged: (String) -> Unit,
    onSupervisorPinChanged: (String) -> Unit,
    onPrintReceiptToggled: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rupiahFormatter = remember { NumberFormat.getIntegerInstance(Locale.forLanguageTag("id-ID")) }
    val countedAmount = countedCashInput.toLongOrNull()

    CassyModalDialog(
        title = stringResource(R.string.shift_close_title),
        subtitle = stringResource(R.string.shift_close_subtitle),
        icon = Icons.Rounded.Lock,
        maxWidth = CassyDimens.ModalWidthStandard,
        dismissEnabled = !isClosing,
        onDismissRequest = onDismiss,
        modifier = modifier,
        footer = {
            CassySecondaryButton(
                text = stringResource(R.string.btn_cancel),
                onClick = onDismiss,
                enabled = !isClosing,
                modifier = Modifier.weight(1f)
            )
            CassyPrimaryButton(
                text = stringResource(R.string.shift_btn_close),
                onClick = onSubmit,
                loading = isClosing,
                containerColor = MaterialTheme.colorScheme.onSurface,
                contentColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.weight(1f)
            )
        }
    ) {
        CassyTextField(
            value = countedCashInput,
            onValueChange = { onCountedCashChanged(it.filter(Char::isDigit).take(12)) },
            label = stringResource(R.string.shift_counted_cash_label),
            placeholder = "0",
            visualTransformation = ThousandsSeparatorTransformation,
            supportingText = if (currentShift?.expectedCash == null) {
                stringResource(R.string.shift_blind_count_hint)
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        if (countedAmount != null && calculatedVariance != null) {
            val isBalance = calculatedVariance == 0L
            val isOver = calculatedVariance > 0L
            val bgBox = when {
                isBalance -> statusOkBg()
                isSupervisorAuthRequired -> statusWarnBg()
                else -> MaterialTheme.colorScheme.background
            }
            val textCol = when {
                isBalance -> statusOkFg()
                isSupervisorAuthRequired -> statusWarnFg()
                else -> MaterialTheme.colorScheme.onSurface
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(CassyDimens.RadiusCard),
                color = bgBox,
                border = BorderStroke(1.dp, textCol.copy(alpha = 0.25f))
            ) {
                Column(modifier = Modifier.padding(CassyDimens.SpaceMd)) {
                    Text(
                        text = stringResource(R.string.shift_variance_status_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val varianceStr = when {
                        isBalance -> stringResource(R.string.shift_variance_balance)
                        isOver -> stringResource(
                            R.string.shift_variance_over,
                            rupiahFormatter.format(calculatedVariance)
                        )
                        else -> stringResource(
                            R.string.shift_variance_short,
                            rupiahFormatter.format(abs(calculatedVariance))
                        )
                    }
                    Text(
                        text = varianceStr,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = textCol
                    )
                }
            }
        }

        if (isSupervisorAuthRequired) {
            CassyErrorBanner(
                message = stringResource(R.string.shift_supervisor_gate_warning),
                tone = CassyToastTone.WARNING
            )

            CassyTextField(
                value = varianceReason,
                onValueChange = onVarianceReasonChanged,
                label = stringResource(R.string.shift_variance_reason_label),
                placeholder = stringResource(R.string.shift_variance_reason_placeholder)
            )

            CassyTextField(
                value = supervisorPinInput,
                onValueChange = onSupervisorPinChanged,
                label = stringResource(R.string.shift_supervisor_pin_label),
                placeholder = "******",
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
            )
        }

        CassyTextField(
            value = notes,
            onValueChange = onNotesChanged,
            label = stringResource(R.string.shift_notes_label),
            placeholder = stringResource(R.string.shift_close_notes_placeholder)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = CassyDimens.TouchTargetMin)
                .toggleable(
                    value = printReceiptOnClose,
                    role = Role.Checkbox,
                    onValueChange = onPrintReceiptToggled
                )
                .padding(horizontal = CassyDimens.SpaceXs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = printReceiptOnClose,
                onCheckedChange = null,
                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
            )
            Spacer(modifier = Modifier.width(CassyDimens.SpaceSm))
            Text(
                text = stringResource(R.string.shift_print_zreport_checkbox),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        if (!errorMessage.isNullOrBlank()) {
            CassyErrorBanner(message = errorMessage, tone = CassyToastTone.ERROR)
        }
    }
}
