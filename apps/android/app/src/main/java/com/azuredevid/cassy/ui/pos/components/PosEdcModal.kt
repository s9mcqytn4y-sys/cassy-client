package com.azuredevid.cassy.ui.pos.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.azuredevid.cassy.R
import com.azuredevid.cassy.ui.designsystem.CassyDimens
import com.azuredevid.cassy.ui.designsystem.CassyErrorBanner
import com.azuredevid.cassy.ui.designsystem.CassyModalDialog
import com.azuredevid.cassy.ui.designsystem.CassyPrimaryButton
import com.azuredevid.cassy.ui.designsystem.CassySecondaryButton
import com.azuredevid.cassy.ui.designsystem.CassyTextField
import com.azuredevid.cassy.ui.designsystem.CassyToastTone
import java.text.NumberFormat

/** Server limit on reconciliation_ref (pos_handler binding max=100). */
private const val EDC_REFERENCE_MAX = 100

/**
 * Records a card payment the cashier already ran on the store's own EDC terminal.
 * Cassy does not talk to the terminal: the cashier copies the slip reference so the
 * server can log the settlement (EDC_RECONCILE audit) against the order total it computed.
 */
@Composable
fun PosEdcModal(
    grandTotal: Long,
    rupiahFormat: NumberFormat,
    isSubmitting: Boolean = false,
    errorMessage: String? = null,
    onDismiss: () -> Unit,
    onConfirmEdcPayment: (reference: String) -> Unit
) {
    var reference by remember { mutableStateOf("") }
    val formattedTotal = stringResource(R.string.cash_chip_rp, rupiahFormat.format(grandTotal))

    CassyModalDialog(
        title = stringResource(R.string.modal_edc_title),
        subtitle = stringResource(R.string.modal_cash_total, formattedTotal),
        icon = Icons.Rounded.CreditCard,
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
                onClick = { onConfirmEdcPayment(reference.trim()) },
                enabled = reference.isNotBlank(),
                loading = isSubmitting,
                modifier = Modifier.weight(1f)
            )
        }
    ) {
        Text(
            text = stringResource(R.string.modal_edc_prompt),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        CassyTextField(
            value = reference,
            onValueChange = { reference = it.take(EDC_REFERENCE_MAX) },
            label = stringResource(R.string.input_edc_reference_label),
            supportingText = stringResource(R.string.input_edc_reference_help)
        )
        if (!errorMessage.isNullOrBlank()) {
            CassyErrorBanner(message = errorMessage, tone = CassyToastTone.ERROR)
        }
    }
}
