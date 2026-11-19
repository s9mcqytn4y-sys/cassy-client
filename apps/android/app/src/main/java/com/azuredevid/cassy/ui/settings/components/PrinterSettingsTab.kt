package com.azuredevid.cassy.ui.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.azuredevid.cassy.R
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = printer.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        text = stringResource(R.string.printer_row_meta, stationLabel(printer.station), printer.paperWidthMm),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!printer.isActive) {
                    CassyStatusBadge(text = stringResource(R.string.printer_inactive), tone = CassyToastTone.WARNING)
                }
            }
            Text(
                text = printer.lanAddress ?: stringResource(R.string.printer_via_agent),
                style = MaterialTheme.typography.bodyMedium,
                color = if (printer.lanAddress != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceSm)) {
                if (printer.lanAddress != null) {
                    CassySecondaryButton(
                        text = stringResource(R.string.printer_btn_test),
                        onClick = onTest,
                        enabled = !isTesting,
                        loading = isTesting,
                        icon = Icons.Rounded.NetworkCheck,
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

/** Add or edit one printer; the server validates the address and the station rules. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PrinterEditorDialog(
    editor: PrinterEditor,
    isSaving: Boolean,
    error: String?,
    onName: (String) -> Unit,
    onStation: (String) -> Unit,
    onAddress: (String) -> Unit,
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
