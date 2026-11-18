package com.azuredevid.cassy.ui.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import com.azuredevid.cassy.R
import com.azuredevid.cassy.ui.designsystem.CassyCard
import com.azuredevid.cassy.ui.designsystem.CassyDimens
import com.azuredevid.cassy.ui.designsystem.CassyErrorBanner
import com.azuredevid.cassy.ui.designsystem.CassyPrimaryButton
import com.azuredevid.cassy.ui.designsystem.CassySecondaryButton
import com.azuredevid.cassy.ui.designsystem.CassySectionHeader
import com.azuredevid.cassy.ui.designsystem.CassyTextField
import com.azuredevid.cassy.ui.designsystem.CassyToastTone
import com.azuredevid.cassy.ui.settings.StoreSettingsUiEvent
import com.azuredevid.cassy.ui.settings.StoreSettingsUiState

@Composable
fun PrinterSettingsTab(
    state: StoreSettingsUiState,
    onEvent: (StoreSettingsUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(CassyDimens.SpaceXl)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(CassyDimens.SpaceLg)
    ) {
        CassyCard {
            CassySectionHeader(
                title = stringResource(R.string.printer_tab_title),
                subtitle = stringResource(R.string.printer_tab_desc)
            )
        }

        CassyCard {
            Column(verticalArrangement = Arrangement.spacedBy(CassyDimens.SpaceMd)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceMd)
                ) {
                    CassyTextField(
                        value = state.cashierPrinterIp,
                        onValueChange = { onEvent(StoreSettingsUiEvent.UpdateCashierPrinterIp(it)) },
                        label = stringResource(R.string.printer_cashier_ip_label),
                        placeholder = stringResource(R.string.printer_ip_placeholder),
                        modifier = Modifier.weight(0.72f)
                    )
                    CassyTextField(
                        value = state.cashierPrinterPort,
                        onValueChange = { onEvent(StoreSettingsUiEvent.UpdateCashierPrinterPort(it)) },
                        label = stringResource(R.string.printer_port_label),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(0.28f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceMd)
                ) {
                    CassyTextField(
                        value = state.kitchenPrinterIp,
                        onValueChange = { onEvent(StoreSettingsUiEvent.UpdateKitchenPrinterIp(it)) },
                        label = stringResource(R.string.printer_kitchen_ip_label),
                        modifier = Modifier.weight(1f)
                    )
                    CassyTextField(
                        value = state.barPrinterIp,
                        onValueChange = { onEvent(StoreSettingsUiEvent.UpdateBarPrinterIp(it)) },
                        label = stringResource(R.string.printer_bar_ip_label),
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceMd)
                ) {
                    CassyTextField(
                        value = state.pastryPrinterIp,
                        onValueChange = { onEvent(StoreSettingsUiEvent.UpdatePastryPrinterIp(it)) },
                        label = stringResource(R.string.printer_pastry_ip_label),
                        modifier = Modifier.weight(1f)
                    )
                    CassyTextField(
                        value = state.expediterPrinterIp,
                        onValueChange = { onEvent(StoreSettingsUiEvent.UpdateExpediterPrinterIp(it)) },
                        label = stringResource(R.string.printer_expediter_ip_label),
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceMd),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.printer_paper_width_label),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = state.paperWidthMm == 58,
                        onClick = { onEvent(StoreSettingsUiEvent.UpdatePaperWidthMm(58)) },
                        label = { Text(stringResource(R.string.printer_paper_58)) },
                        modifier = Modifier.defaultMinSize(minHeight = CassyDimens.TouchTargetMin)
                    )
                    FilterChip(
                        selected = state.paperWidthMm == 80,
                        onClick = { onEvent(StoreSettingsUiEvent.UpdatePaperWidthMm(80)) },
                        label = { Text(stringResource(R.string.printer_paper_80)) },
                        modifier = Modifier.defaultMinSize(minHeight = CassyDimens.TouchTargetMin)
                    )
                }
            }
        }

        if (state.printerTestMessage != null) {
            val tone = when (state.printerTestOk) {
                true -> CassyToastTone.SUCCESS
                false -> CassyToastTone.ERROR
                null -> CassyToastTone.INFO
            }
            CassyErrorBanner(
                message = state.printerTestMessage.asString(),
                tone = tone
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceLg)
        ) {
            CassySecondaryButton(
                text = stringResource(R.string.printer_btn_test),
                onClick = { onEvent(StoreSettingsUiEvent.TestCashierPrinter) },
                enabled = !state.isTestingPrinter && !state.isSavingPrinter,
                loading = state.isTestingPrinter,
                icon = Icons.Rounded.NetworkCheck,
                modifier = Modifier.weight(1f)
            )
            CassyPrimaryButton(
                text = stringResource(
                    if (state.isSavingPrinter) R.string.printer_btn_saving else R.string.printer_btn_save
                ),
                onClick = { onEvent(StoreSettingsUiEvent.SavePrinterConfig) },
                enabled = !state.isTestingPrinter && !state.isSavingPrinter,
                loading = state.isSavingPrinter,
                icon = Icons.Rounded.Save,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
