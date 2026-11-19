package com.azuredevid.cassy.ui.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.azuredevid.cassy.R
import com.azuredevid.cassy.core.hardware.PrinterPort9100Manager
import com.azuredevid.cassy.domain.repository.PrinterSettingsRepository
import com.azuredevid.cassy.domain.repository.StorePrinter
import com.azuredevid.cassy.domain.repository.StorePrinterInput
import com.azuredevid.cassy.domain.repository.StorePrinterRepository
import com.azuredevid.cassy.domain.repository.toPrinterConfig
import com.azuredevid.cassy.ui.designsystem.UiText
import com.azuredevid.cassy.ui.designsystem.toUiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The add or edit form of one printer; [id] is null for a new printer. */
@Immutable
data class PrinterEditor(
    val id: String? = null,
    val name: String = "",
    val station: String = "receipt",
    val lanAddress: String = "",
    val paperWidthMm: Int = 80
) {
    val canSave: Boolean get() = name.isNotBlank() && (station != "receipt" || lanAddress.isNotBlank())
}

@Immutable
data class PrinterSettingsUiState(
    val canManage: Boolean = false,
    val isLoading: Boolean = false,
    val printers: List<StorePrinter> = emptyList(),
    val editor: PrinterEditor? = null,
    val isSaving: Boolean = false,
    val testingId: String? = null,
    val messageOk: Boolean? = null,
    val message: UiText? = null
)

/**
 * Printers are configured once on the server and every tablet syncs them. Owners and managers add or
 * edit printers here; other staff see the list read-only. Each load refreshes the local print cache,
 * which the POS keeps using when the server is out of reach.
 */
class PrinterSettingsViewModel(
    private val printers: StorePrinterRepository,
    private val printerSettings: PrinterSettingsRepository,
    private val printerManager: PrinterPort9100Manager
) : ViewModel() {

    private val _uiState = MutableStateFlow(PrinterSettingsUiState())
    val uiState: StateFlow<PrinterSettingsUiState> = _uiState.asStateFlow()

    fun load(canManage: Boolean) {
        _uiState.update { it.copy(canManage = canManage, isLoading = true, message = null) }
        viewModelScope.launch { refresh() }
    }

    fun openNew() = openEditor(PrinterEditor())

    fun openEdit(printer: StorePrinter) = openEditor(
        PrinterEditor(printer.id, printer.name, printer.station, printer.lanAddress.orEmpty(), printer.paperWidthMm)
    )

    fun dismissEditor() {
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(editor = null, message = null) }
    }

    fun setName(value: String) = edit { it.copy(name = value) }

    fun setStation(value: String) = edit { it.copy(station = value) }

    fun setLanAddress(value: String) = edit { it.copy(lanAddress = value.filter { ch -> ch.isDigit() || ch == '.' || ch == ':' }) }

    fun setPaperWidth(value: Int) = edit { it.copy(paperWidthMm = value) }

    fun save() {
        val state = _uiState.value
        val editor = state.editor ?: return
        if (!state.canManage || !editor.canSave || state.isSaving) return
        val input = StorePrinterInput(editor.name, editor.station, editor.lanAddress, editor.paperWidthMm)
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, message = null) }
            val result = editor.id?.let { printers.update(it, input) } ?: printers.create(input)
            result
                .onSuccess {
                    _uiState.update { s -> s.copy(isSaving = false, editor = null, message = UiText.res(R.string.printer_msg_saved), messageOk = true) }
                    refresh()
                }
                .onFailure { err ->
                    _uiState.update { s -> s.copy(isSaving = false, message = err.toUiText(R.string.printer_msg_save_failed), messageOk = false) }
                }
        }
    }

    /** Opens a TCP connection to the printer's port to show whether the tablet can reach it. */
    fun test(printer: StorePrinter) {
        if (printer.lanAddress == null || _uiState.value.testingId != null) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(testingId = printer.id, messageOk = null, message = UiText.res(R.string.settings_msg_printer_checking, printer.host, printer.port))
            }
            val result = printerManager.checkPrinterHealth(printer.host, printer.port)
            val ok = result.health == PrinterPort9100Manager.PrinterHealth.READY
            _uiState.update {
                it.copy(
                    testingId = null,
                    messageOk = ok,
                    message = UiText.res(if (ok) R.string.printer_msg_test_ok else R.string.printer_msg_test_failed, printer.name)
                )
            }
        }
    }

    private suspend fun refresh() {
        val result = if (_uiState.value.canManage) printers.listAll() else printers.listForTablet()
        result
            .onSuccess { list ->
                printerSettings.saveConfig(list.toPrinterConfig())
                _uiState.update { it.copy(isLoading = false, printers = list) }
            }
            .onFailure { err ->
                _uiState.update { it.copy(isLoading = false, messageOk = false, message = err.toUiText(R.string.printer_msg_load_failed)) }
            }
    }

    private fun openEditor(editor: PrinterEditor) {
        if (!_uiState.value.canManage) return
        _uiState.update { it.copy(editor = editor, message = null) }
    }

    private fun edit(change: (PrinterEditor) -> PrinterEditor) {
        _uiState.update { state -> state.editor?.let { state.copy(editor = change(it)) } ?: state }
    }
}
