package com.azuredevid.cassy.ui.shift.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.azuredevid.cassy.R
import com.azuredevid.cassy.domain.model.XReport
import com.azuredevid.cassy.ui.designsystem.CassyCard
import com.azuredevid.cassy.ui.designsystem.CassyDimens
import com.azuredevid.cassy.ui.designsystem.CassyErrorBanner
import com.azuredevid.cassy.ui.designsystem.CassyModalDialog
import com.azuredevid.cassy.ui.designsystem.CassyPrimaryButton
import com.azuredevid.cassy.ui.designsystem.CassySecondaryButton
import com.azuredevid.cassy.ui.designsystem.CassyToastTone
import java.text.NumberFormat
import java.util.Locale

@Composable
fun XReportModal(
    report: XReport?,
    isLoading: Boolean,
    isPrinting: Boolean,
    onPrint: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rupiahFormatter = remember { NumberFormat.getIntegerInstance(Locale("id", "ID")) }

    CassyModalDialog(
        title = stringResource(R.string.shift_xreport_title),
        icon = Icons.AutoMirrored.Rounded.ReceiptLong,
        maxWidth = CassyDimens.ModalWidthNarrow,
        onDismissRequest = onDismiss,
        modifier = modifier,
        footer = {
            CassySecondaryButton(
                text = stringResource(R.string.shift_xreport_close_btn),
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            )
            CassyPrimaryButton(
                text = stringResource(R.string.shift_xreport_print_btn),
                onClick = onPrint,
                enabled = report != null && !isLoading,
                loading = isPrinting,
                icon = Icons.Rounded.Print,
                modifier = Modifier.weight(1f)
            )
        }
    ) {
        when {
            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = CassyDimens.SpaceXl),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }

            report != null -> {
                val s = report.summary
                CassyCard(
                    containerColor = MaterialTheme.colorScheme.background,
                    contentPadding = CassyDimens.SpaceLg
                ) {
                    XReportRow(
                        label = stringResource(R.string.shift_xreport_opening_float),
                        value = stringResource(R.string.cash_chip_rp, rupiahFormatter.format(s.openingFloat))
                    )
                    XReportRow(
                        label = stringResource(R.string.shift_xreport_cash_sales),
                        value = stringResource(R.string.cash_chip_rp, rupiahFormatter.format(s.cashSales))
                    )
                    if (s.cashIns > 0L) {
                        XReportRow(
                            label = stringResource(R.string.shift_xreport_cash_ins),
                            value = stringResource(R.string.cash_chip_rp, rupiahFormatter.format(s.cashIns))
                        )
                    }
                    if (s.paidOuts > 0L) {
                        XReportRow(
                            label = stringResource(R.string.shift_xreport_paid_outs),
                            value = "-${stringResource(R.string.cash_chip_rp, rupiahFormatter.format(s.paidOuts))}"
                        )
                    }
                    if (s.cashRefunds > 0L) {
                        XReportRow(
                            label = stringResource(R.string.shift_xreport_refunds),
                            value = "-${stringResource(R.string.cash_chip_rp, rupiahFormatter.format(s.cashRefunds))}"
                        )
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = CassyDimens.SpaceXs),
                        color = MaterialTheme.colorScheme.outline
                    )
                    val expectedValue = if (report.expectedRedacted || report.expectedCash == null) {
                        stringResource(R.string.shift_xreport_expected_redacted)
                    } else {
                        stringResource(R.string.cash_chip_rp, rupiahFormatter.format(report.expectedCash))
                    }
                    XReportRow(
                        label = stringResource(R.string.shift_xreport_expected_cash),
                        value = expectedValue,
                        emphasize = true
                    )
                }

                if (report.unsettledOpenTabsCount > 0) {
                    CassyErrorBanner(
                        message = stringResource(
                            R.string.shift_xreport_unsettled_tabs,
                            report.unsettledOpenTabsCount,
                            rupiahFormatter.format(report.unsettledOpenTabsTotalAmount)
                        ),
                        tone = CassyToastTone.WARNING
                    )
                }
            }

            else -> {
                CassyErrorBanner(
                    message = stringResource(R.string.shift_xreport_empty),
                    tone = CassyToastTone.WARNING
                )
            }
        }
    }
}

@Composable
private fun XReportRow(
    label: String,
    value: String,
    emphasize: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = CassyDimens.SpaceXxs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (emphasize) FontWeight.Bold else FontWeight.Normal,
            color = if (emphasize) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
        Text(
            text = value,
            style = if (emphasize) {
                MaterialTheme.typography.titleMedium
            } else {
                MaterialTheme.typography.bodyMedium
            },
            fontWeight = if (emphasize) FontWeight.Bold else FontWeight.SemiBold,
            color = if (emphasize) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
    }
}
