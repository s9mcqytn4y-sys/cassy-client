package com.azuredevid.cassy.ui.pos.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.azuredevid.cassy.R
import com.azuredevid.cassy.ui.designsystem.CassyDimens
import com.azuredevid.cassy.ui.designsystem.CassyErrorBanner
import com.azuredevid.cassy.ui.designsystem.CassyModalDialog
import com.azuredevid.cassy.ui.designsystem.CassyPrimaryButton
import com.azuredevid.cassy.ui.designsystem.CassySecondaryButton
import com.azuredevid.cassy.ui.designsystem.CassyTextField
import com.azuredevid.cassy.ui.designsystem.CassyToastTone
import com.azuredevid.cassy.ui.theme.statusErrBg
import com.azuredevid.cassy.ui.theme.statusErrFg
import com.azuredevid.cassy.ui.theme.statusOkBg
import com.azuredevid.cassy.ui.theme.statusOkFg
import java.text.NumberFormat

/**
 * Cash settlement modal with full rupiah preset chips, manual tender input, and server-backed submit.
 */
@Composable
fun PosCashModal(
    grandTotal: Long,
    rupiahFormat: NumberFormat,
    isSubmitting: Boolean = false,
    errorMessage: String? = null,
    onDismiss: () -> Unit,
    onConfirmCashPayment: (tendered: Long, change: Long) -> Unit
) {
    var cashTenderedStr by remember { mutableStateOf(grandTotal.toString()) }
    val tenderedLong = cashTenderedStr.toLongOrNull() ?: 0L
    val change = tenderedLong - grandTotal
    val isSufficient = tenderedLong >= grandTotal
    val formattedTotal = stringResource(R.string.cash_chip_rp, rupiahFormat.format(grandTotal))
    val quickDenoms = remember { listOf(50_000L, 100_000L, 200_000L) }

    CassyModalDialog(
        title = stringResource(R.string.modal_cash_title),
        subtitle = stringResource(R.string.modal_cash_total, formattedTotal),
        icon = Icons.Rounded.Payments,
        maxWidth = CassyDimens.ModalWidthStandard,
        dismissEnabled = !isSubmitting,
        onDismissRequest = onDismiss,
        footer = {
            CassySecondaryButton(
                text = stringResource(R.string.btn_cancel),
                onClick = onDismiss,
                enabled = !isSubmitting,
                modifier = Modifier.weight(1f)
            )
            CassyPrimaryButton(
                text = stringResource(R.string.btn_complete_and_print),
                onClick = { onConfirmCashPayment(tenderedLong, change) },
                enabled = isSufficient,
                loading = isSubmitting,
                modifier = Modifier.weight(1f)
            )
        }
    ) {
        Text(
            text = stringResource(R.string.modal_cash_quick_prompt),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val isCompact = maxWidth < 420.dp
            if (isCompact) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(CassyDimens.SpaceXs)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceXs)
                    ) {
                        CashChipButton(
                            label = stringResource(R.string.chip_exact_cash),
                            selected = tenderedLong == grandTotal,
                            onClick = { cashTenderedStr = grandTotal.toString() },
                            modifier = Modifier.weight(1f)
                        )
                        CashChipButton(
                            label = stringResource(R.string.cash_chip_rp, rupiahFormat.format(quickDenoms[0])),
                            selected = tenderedLong == quickDenoms[0],
                            onClick = { cashTenderedStr = quickDenoms[0].toString() },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceXs)
                    ) {
                        CashChipButton(
                            label = stringResource(R.string.cash_chip_rp, rupiahFormat.format(quickDenoms[1])),
                            selected = tenderedLong == quickDenoms[1],
                            onClick = { cashTenderedStr = quickDenoms[1].toString() },
                            modifier = Modifier.weight(1f)
                        )
                        CashChipButton(
                            label = stringResource(R.string.cash_chip_rp, rupiahFormat.format(quickDenoms[2])),
                            selected = tenderedLong == quickDenoms[2],
                            onClick = { cashTenderedStr = quickDenoms[2].toString() },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceXs)
                ) {
                    CashChipButton(
                        label = stringResource(R.string.chip_exact_cash),
                        selected = tenderedLong == grandTotal,
                        onClick = { cashTenderedStr = grandTotal.toString() },
                        modifier = Modifier.weight(1f)
                    )
                    quickDenoms.forEach { preset ->
                        CashChipButton(
                            label = stringResource(R.string.cash_chip_rp, rupiahFormat.format(preset)),
                            selected = tenderedLong == preset,
                            onClick = { cashTenderedStr = preset.toString() },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        CassyTextField(
            value = cashTenderedStr,
            onValueChange = { cashTenderedStr = it.filter { ch -> ch.isDigit() } },
            label = stringResource(R.string.input_cash_tendered_label),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        Surface(
            shape = RoundedCornerShape(CassyDimens.RadiusCard),
            color = if (isSufficient) statusOkBg() else statusErrBg(),
            border = BorderStroke(
                1.dp,
                (if (isSufficient) statusOkFg() else statusErrFg()).copy(alpha = 0.25f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(CassyDimens.SpaceMd)) {
                if (isSufficient) {
                    Text(
                        text = stringResource(
                            R.string.change_label,
                            stringResource(R.string.cash_chip_rp, rupiahFormat.format(change))
                        ),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = statusOkFg()
                    )
                } else {
                    Text(
                        text = stringResource(
                            R.string.insufficient_cash,
                            stringResource(R.string.cash_chip_rp, rupiahFormat.format(grandTotal - tenderedLong))
                        ),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        color = statusErrFg()
                    )
                }
            }
        }

        if (!errorMessage.isNullOrBlank()) {
            CassyErrorBanner(message = errorMessage, tone = CassyToastTone.ERROR)
        }
    }
}

@Composable
private fun CashChipButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(CassyDimens.RadiusButton),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
            contentColor = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        ),
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        ),
        contentPadding = PaddingValues(horizontal = CassyDimens.SpaceXs, vertical = CassyDimens.SpaceXs),
        modifier = modifier.defaultMinSize(minHeight = CassyDimens.TouchTargetMin)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}
