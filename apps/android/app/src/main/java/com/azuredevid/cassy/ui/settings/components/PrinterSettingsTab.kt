package com.azuredevid.cassy.ui.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.SettingsBluetooth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.azuredevid.cassy.R
import com.azuredevid.cassy.core.hardware.PairedBluetoothDevice
import com.azuredevid.cassy.domain.repository.PrinterConfig
import com.azuredevid.cassy.domain.repository.StorePrinter
import com.azuredevid.cassy.ui.designsystem.CassyCard
import com.azuredevid.cassy.ui.designsystem.CassyDimens
import com.azuredevid.cassy.ui.designsystem.CassyEmptyState
import com.azuredevid.cassy.ui.designsystem.CassyErrorBanner
import com.azuredevid.cassy.ui.designsystem.CassyModalDialog
import com.azuredevid.cassy.ui.designsystem.CassyPrimaryButton
import com.azuredevid.cassy.ui.designsystem.CassySecondaryButton
import com.azuredevid.cassy.ui.designsystem.CassySectionHeader
import com.azuredevid.cassy.ui.designsystem.CassyStatusBadge
import com.azuredevid.cassy.ui.designsystem.CassyTextField
import com.azuredevid.cassy.ui.designsystem.CassyToastTone
import com.azuredevid.cassy.ui.settings.PrinterEditor
import com.azuredevid.cassy.ui.settings.PrinterSettingsUiState

/** The store's printers from the server: owners add and edit them, every tablet tests and uses them. */
@Composable
fun PrinterSettingsTab(
    state: PrinterSettingsUiState,
    onAdd: () -> Unit,
    onEdit: (StorePrinter) -> Unit,
    onTest: (StorePrinter) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(CassyDimens.SpaceXl)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(CassyDimens.SpaceLg)
    ) {
        CassyCard {
            CassySectionHeader(
                title = stringResource(R.string.printer_tab_title),
                subtitle = stringResource(if (state.canManage) R.string.printer_tab_desc else R.string.printer_tab_desc_readonly)
            )
        }

        state.message?.takeIf { state.editor == null }?.let {
            CassyErrorBanner(message = it.asString(), tone = toneOf(state.messageOk))
        }

        if (state.canManage) {
            CassyPrimaryButton(
                text = stringResource(R.string.printer_btn_add),
                onClick = onAdd,
                icon = Icons.Rounded.Add,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (state.printers.isEmpty() && !state.isLoading) {
            CassyEmptyState(
                icon = Icons.Rounded.Print,
                title = stringResource(R.string.printer_empty_title),
                subtitle = stringResource(R.string.printer_empty_body)
            )
        }

        state.printers.forEach { printer ->
            PrinterRow(
                printer = printer,
                canEdit = state.canManage && printer.station in StorePrinter.TABLET_STATIONS,
                isTesting = state.testingId == printer.id,
                onEdit = { onEdit(printer) },
                onTest = { onTest(printer) }
            )
        }
    }
}

@Composable
private fun PrinterRow(printer: StorePrinter, canEdit: Boolean, isTesting: Boolean, onEdit: () -> Unit, onTest: () -> Unit) {
    CassyCard {
        Column(verticalArrangement = Arrangement.spacedBy(CassyDimens.SpaceSm)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceSm)) {
                Icon(
                    imageVector = if (printer.isBluetooth) Icons.Rounded.Bluetooth else Icons.Rounded.Router,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = printer.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        text = stringResource(R.string.printer_row_meta, stationLabel(printer.station), printer.paperWidthMm),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                CassyStatusBadge(
                    text = stringResource(
                        when {
                            printer.isBluetooth -> R.string.printer_badge_bluetooth
                            printer.lanAddress != null -> R.string.printer_badge_lan
                            else -> R.string.printer_badge_agent
                        }
                    ),
                    tone = if (printer.hasDirectTarget) CassyToastTone.INFO else CassyToastTone.WARNING
                )
                if (!printer.isActive) {
                    CassyStatusBadge(text = stringResource(R.string.printer_inactive), tone = CassyToastTone.WARNING)
                }
            }
            Text(
                text = printer.displayAddress ?: stringResource(R.string.printer_via_agent),
                style = MaterialTheme.typography.bodyMedium,
                color = if (printer.hasDirectTarget) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceSm)) {
                if (printer.hasDirectTarget) {
                    CassySecondaryButton(
                        text = stringResource(R.string.printer_btn_test),
                        onClick = onTest,
                        enabled = !isTesting,
                        loading = isTesting,
                        icon = if (printer.isBluetooth) Icons.Rounded.Bluetooth else Icons.Rounded.NetworkCheck,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (canEdit) {
                    CassySecondaryButton(
                        text = stringResource(R.string.printer_btn_edit),
                        onClick = onEdit,
                        icon = Icons.Rounded.Edit,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/** Add or edit one printer; supports both LAN TCP 9100 and Bluetooth Classic SPP (58mm/80mm). */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PrinterEditorDialog(
    editor: PrinterEditor,
    isSaving: Boolean,
    error: String?,
    pairedBluetoothDevices: List<PairedBluetoothDevice> = emptyList(),
    hasBluetoothPermission: Boolean = true,
    isBluetoothEnabled: Boolean = true,
    onName: (String) -> Unit,
    onStation: (String) -> Unit,
    onTransport: (String) -> Unit = {},
    onAddress: (String) -> Unit,
    onBluetoothMac: (String) -> Unit = {},
    onSelectPairedDevice: (PairedBluetoothDevice) -> Unit = {},
    onRefreshBluetooth: () -> Unit = {},
    onRequestBluetoothPermission: () -> Unit = {},
    onOpenBluetoothSettings: () -> Unit = {},
    onPaper: (Int) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    CassyModalDialog(
        title = stringResource(if (editor.id == null) R.string.printer_editor_new else R.string.printer_editor_edit),
        icon = Icons.Rounded.Print,
        dismissEnabled = !isSaving,
        onDismissRequest = onDismiss,
        footer = {
            CassySecondaryButton(
                text = stringResource(R.string.btn_cancel),
                onClick = onDismiss,
                enabled = !isSaving,
                modifier = Modifier.weight(1f)
            )
            CassyPrimaryButton(
                text = stringResource(R.string.printer_btn_save),
                onClick = onSave,
                enabled = editor.canSave && !isSaving,
                loading = isSaving,
                modifier = Modifier.weight(1f)
            )
        }
    ) {
        CassyTextField(
            value = editor.name,
            onValueChange = onName,
            label = stringResource(R.string.printer_field_name),
            placeholder = stringResource(R.string.printer_field_name_hint),
            enabled = !isSaving
        )
        Text(text = stringResource(R.string.printer_field_station), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceSm)) {
            StorePrinter.TABLET_STATIONS.forEach { station ->
                FilterChip(
                    selected = editor.station == station,
                    onClick = { onStation(station) },
                    enabled = !isSaving,
                    label = { Text(stationLabel(station)) },
                    modifier = Modifier.defaultMinSize(minHeight = CassyDimens.TouchTargetMin)
                )
            }
        }
        Text(text = stringResource(R.string.printer_field_transport), style = MaterialTheme.typography.labelLarge)
        val transportOptions = listOf(
            PrinterConfig.TRANSPORT_LAN to R.string.printer_transport_lan,
            PrinterConfig.TRANSPORT_BLUETOOTH to R.string.printer_transport_bluetooth
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            transportOptions.forEachIndexed { index, (value, labelRes) ->
                val selected = if (value == PrinterConfig.TRANSPORT_BLUETOOTH) editor.isBluetooth else !editor.isBluetooth
                SegmentedButton(
                    selected = selected,
                    onClick = { onTransport(value) },
                    enabled = !isSaving,
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = transportOptions.size),
                    modifier = Modifier.defaultMinSize(minHeight = CassyDimens.TouchTargetMin),
                    icon = {
                        Icon(
                            imageVector = if (value == PrinterConfig.TRANSPORT_BLUETOOTH) Icons.Rounded.Bluetooth else Icons.Rounded.Router,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                ) {
                    Text(text = stringResource(labelRes))
                }
            }
        }
        if (editor.isBluetooth) {
            BluetoothPrinterSection(
                bluetoothMac = editor.bluetoothMac,
                isSaving = isSaving,
                pairedDevices = pairedBluetoothDevices,
                hasPermission = hasBluetoothPermission,
                isEnabled = isBluetoothEnabled,
                onBluetoothMac = onBluetoothMac,
                onSelectPairedDevice = onSelectPairedDevice,
                onRefresh = onRefreshBluetooth,
                onRequestPermission = onRequestBluetoothPermission,
                onOpenSettings = onOpenBluetoothSettings
            )
        } else {
            CassyTextField(
                value = editor.lanAddress,
                onValueChange = onAddress,
                label = stringResource(R.string.printer_field_address),
                placeholder = stringResource(R.string.printer_field_address_hint),
                enabled = !isSaving
            )
            Text(
                text = stringResource(if (editor.station == "receipt") R.string.printer_address_required else R.string.printer_address_optional),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceSm)) {
            Text(
                text = stringResource(R.string.printer_paper_width_label),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f)
            )
            listOf(58 to R.string.printer_paper_58, 80 to R.string.printer_paper_80).forEach { (width, label) ->
                FilterChip(
                    selected = editor.paperWidthMm == width,
                    onClick = { onPaper(width) },
                    enabled = !isSaving,
                    label = { Text(stringResource(label)) },
                    modifier = Modifier.defaultMinSize(minHeight = CassyDimens.TouchTargetMin)
                )
            }
        }
        error?.let { CassyErrorBanner(message = it, tone = CassyToastTone.ERROR) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BluetoothPrinterSection(
    bluetoothMac: String,
    isSaving: Boolean,
    pairedDevices: List<PairedBluetoothDevice>,
    hasPermission: Boolean,
    isEnabled: Boolean,
    onBluetoothMac: (String) -> Unit,
    onSelectPairedDevice: (PairedBluetoothDevice) -> Unit,
    onRefresh: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(CassyDimens.SpaceSm)) {
        if (!hasPermission) {
            CassyErrorBanner(
                message = stringResource(R.string.printer_bt_permission_needed),
                tone = CassyToastTone.WARNING
            )
            CassySecondaryButton(
                text = stringResource(R.string.printer_bt_btn_grant_permission),
                onClick = onRequestPermission,
                icon = Icons.Rounded.Bluetooth,
                modifier = Modifier.fillMaxWidth()
            )
        } else if (!isEnabled) {
            CassyErrorBanner(
                message = stringResource(R.string.printer_bt_disabled),
                tone = CassyToastTone.WARNING
            )
            CassySecondaryButton(
                text = stringResource(R.string.printer_bt_btn_open_settings),
                onClick = onOpenSettings,
                icon = Icons.Rounded.SettingsBluetooth,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceSm),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.printer_bt_paired_title),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f)
                )
                CassySecondaryButton(
                    text = stringResource(R.string.printer_bt_btn_refresh),
                    onClick = onRefresh,
                    enabled = !isSaving,
                    icon = Icons.Rounded.Refresh
                )
            }
            if (pairedDevices.isEmpty()) {
                Text(
                    text = stringResource(R.string.printer_bt_paired_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                CassySecondaryButton(
                    text = stringResource(R.string.printer_bt_btn_open_settings),
                    onClick = onOpenSettings,
                    icon = Icons.Rounded.SettingsBluetooth,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceSm),
                    verticalArrangement = Arrangement.spacedBy(CassyDimens.SpaceXs)
                ) {
                    pairedDevices.forEach { device ->
                        FilterChip(
                            selected = bluetoothMac.equals(device.macAddress, ignoreCase = true),
                            onClick = { onSelectPairedDevice(device) },
                            enabled = !isSaving,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Bluetooth,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = { Text("${device.name} (${device.macAddress})") },
                            modifier = Modifier.defaultMinSize(minHeight = CassyDimens.TouchTargetMin)
                        )
                    }
                }
            }
        }
        CassyTextField(
            value = bluetoothMac,
            onValueChange = onBluetoothMac,
            label = stringResource(R.string.printer_field_bt_mac),
            placeholder = stringResource(R.string.printer_field_bt_mac_hint),
            enabled = !isSaving
        )
        Text(
            text = stringResource(R.string.printer_bt_mac_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun stationLabel(station: String): String = stringResource(
    when (station) {
        "receipt" -> R.string.printer_station_receipt
        "bar" -> R.string.printer_station_bar
        "kitchen" -> R.string.printer_station_kitchen
        "checker" -> R.string.printer_station_checker
        else -> R.string.printer_station_label
    }
)

private fun toneOf(ok: Boolean?) = when (ok) {
    true -> CassyToastTone.SUCCESS
    false -> CassyToastTone.ERROR
    null -> CassyToastTone.INFO
}
