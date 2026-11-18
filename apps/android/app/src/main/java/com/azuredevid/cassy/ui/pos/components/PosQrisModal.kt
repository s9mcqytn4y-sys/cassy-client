package com.azuredevid.cassy.ui.pos.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.azuredevid.cassy.R
import com.azuredevid.cassy.core.util.QrBitmapGenerator
import com.azuredevid.cassy.domain.model.PosPaymentAttempt
import com.azuredevid.cassy.ui.designsystem.CassyDimens
import com.azuredevid.cassy.ui.designsystem.CassyModalDialog
import com.azuredevid.cassy.ui.designsystem.CassyPrimaryButton
import com.azuredevid.cassy.ui.designsystem.CassySecondaryButton
import com.azuredevid.cassy.ui.designsystem.CassyStatusBadge
import com.azuredevid.cassy.ui.designsystem.CassyToastTone
import java.text.NumberFormat

/**
 * Dynamic Midtrans QRIS modal built on CassyModalDialog with clear polling status and touch targets.
 */
@Composable
fun PosQrisModal(
    orderNumber: String,
    payment: PosPaymentAttempt,
    rupiahFormat: NumberFormat,
    isCheckingStatus: Boolean,
    onCheckStatus: () -> Unit,
    onDismiss: () -> Unit
) {
    val qrBitmap = remember(payment.qrString) {
        QrBitmapGenerator.createQrBitmap(payment.qrString, width = 440, height = 440)
    }
    val intFormat = remember { NumberFormat.getIntegerInstance(Locale("id", "ID")) }
    val formattedAmount = stringResource(R.string.cash_chip_rp, intFormat.format(payment.amount))
    val expiresEpochMs = remember(payment.expiresAt) {
        payment.expiresAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
    }
    var nowMs by remember(payment.expiresAt) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(expiresEpochMs) {
        if (expiresEpochMs == null) return@LaunchedEffect
        while (nowMs < expiresEpochMs) {
            delay(1_000L)
            nowMs = System.currentTimeMillis()
        }
    }
    val remainingSec = expiresEpochMs?.let { ((it - nowMs) / 1000L).coerceAtLeast(0L) }
    val isExpired = remainingSec != null && remainingSec == 0L

    CassyModalDialog(
        title = stringResource(R.string.modal_qris_title),
        subtitle = if (orderNumber.isNotBlank()) {
            stringResource(R.string.qris_order_number_label, orderNumber)
        } else {
            null
        },
        icon = Icons.Rounded.QrCode2,
        maxWidth = CassyDimens.ModalWidthNarrow,
        onDismissRequest = onDismiss,
        footer = {
            CassySecondaryButton(
                text = stringResource(R.string.btn_cancel_qris),
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            )
            CassyPrimaryButton(
                text = stringResource(R.string.btn_force_sync),
                onClick = onCheckStatus,
                loading = isCheckingStatus,
                modifier = Modifier.weight(1f)
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(CassyDimens.SpaceMd)
        ) {
            Text(
                text = stringResource(R.string.qris_total_label, formattedAmount),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            // QR codes must always sit on a high-contrast white quiet zone for camera scanners.
            Surface(
                shape = RoundedCornerShape(CassyDimens.RadiusCard),
                color = Color.White,
                tonalElevation = 2.dp
            ) {
                if (qrBitmap != null) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.modal_qris_title),
                        modifier = Modifier
                            .size(220.dp)
                            .padding(CassyDimens.SpaceMd)
                    )
                } else {
                    Box(
                        modifier = Modifier.size(220.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            Text(
                text = stringResource(R.string.qris_payment_instructions),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            if (expiresEpochMs != null && remainingSec != null) {
                val timeStr = remember(expiresEpochMs) {
                    DateTimeFormatter.ofPattern("HH:mm")
                        .withZone(ZoneId.systemDefault())
                        .format(Instant.ofEpochMilli(expiresEpochMs))
                }
                val mmSs = "%02d:%02d".format(remainingSec / 60L, remainingSec % 60L)
                CassyStatusBadge(
                    text = if (isExpired) {
                        stringResource(R.string.qris_expired_badge)
                    } else {
                        stringResource(R.string.qris_countdown_format, timeStr, mmSs)
                    },
                    tone = when {
                        isExpired -> CassyToastTone.ERROR
                        remainingSec <= 60L -> CassyToastTone.WARNING
                        else -> CassyToastTone.INFO
                    }
                )
            } else if (!payment.expiresAt.isNullOrBlank()) {
                Text(
                    text = stringResource(R.string.qris_expiry_format, payment.expiresAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceSm)
            ) {
                CassyStatusBadge(
                    text = if (isCheckingStatus) {
                        stringResource(R.string.qris_syncing_status)
                    } else {
                        stringResource(R.string.qris_waiting_status)
                    },
                    tone = if (isCheckingStatus) CassyToastTone.INFO else CassyToastTone.WARNING
                )
            }
        }
    }
}
