package com.azuredevid.cassy.ui.tables

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.azuredevid.cassy.R
import com.azuredevid.cassy.core.hardware.PrinterPort9100Manager
import com.azuredevid.cassy.core.hardware.TableQrPosterBuilder
import com.azuredevid.cassy.core.hardware.TableQrPosterCopy
import com.azuredevid.cassy.core.security.SessionManager
import com.azuredevid.cassy.core.util.TableQrUrl
import com.azuredevid.cassy.core.util.TableTentCardPdfRenderer
import com.azuredevid.cassy.core.util.TentCardContent
import com.azuredevid.cassy.data.repository.ServerSettingsRepository
import com.azuredevid.cassy.domain.error.DomainException
import com.azuredevid.cassy.domain.model.QrRotationBlock
import com.azuredevid.cassy.domain.model.TableDraft
import com.azuredevid.cassy.domain.model.TableEntry
import com.azuredevid.cassy.domain.repository.PrinterSettingsRepository
import com.azuredevid.cassy.domain.repository.TableManagementRepository
import com.azuredevid.cassy.ui.designsystem.CassyToastTone
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream

/** A message for the snackbar: a string resource, or text the backend already wrote for the user. */
sealed interface TableMessage {
    data class Res(@StringRes val id: Int, val arg: String? = null) : TableMessage
    data class Server(val text: String) : TableMessage
}

/** Screen state of table management; immutable so Compose can skip unchanged cards. */
@Immutable
data class TableManagementUiState(
    val tables: List<TableEntry> = emptyList(),
    val selectedTableId: String? = null,
    val canManageTables: Boolean = false,
    val canManageQr: Boolean = false,
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val isPrinting: Boolean = false,
    val isExporting: Boolean = false,
    val serverBaseUrl: String = "",
    val loadError: TableMessage? = null
) {
    val selectedTable: TableEntry?
        get() = tables.firstOrNull { it.id == selectedTableId }

    /** Guest URL of the selected table for the on-screen preview; null when the QR is hidden or unusable. */
    val selectedQrUrl: String?
        get() = selectedTable?.takeIf { canManageQr }?.let { TableQrUrl.build(serverBaseUrl, it.qrToken) }

    /** True while any network or printer action runs; actions stay disabled to avoid double submits. */
    val isBusy: Boolean
        get() = isSubmitting || isPrinting || isExporting
}

/** One-shot events for the screen. */
sealed interface TableManagementUiEffect {
    data class ShowMessage(val message: TableMessage, val tone: CassyToastTone = CassyToastTone.INFO) : TableManagementUiEffect
    /** The create or edit form succeeded and should close. */
    data object FormSaved : TableManagementUiEffect
}

/**
 * Table, QR rotation, and QR poster management (Release One PR 5).
 *
 * Fail-closed rules:
 * - No staff session: nothing loads and every management action stays hidden.
 * - Actions are shown only for `table.manage` / `table.qr.manage`; the backend re-checks each call.
 * - [confirmRotateQr] reloads the board right before rotating and refuses when the table has an open
 *   or merged session, unpaid orders, or when the fresh state cannot be read. The backend does not
 *   check this itself, so this guard is the only protection against killing a seated guest's QR.
 *
 * The selected table survives process death through [SavedStateHandle].
 */
class TableManagementViewModel(
    private val repository: TableManagementRepository,
    private val sessionManager: SessionManager,
    private val serverSettings: ServerSettingsRepository,
    private val printerSettings: PrinterSettingsRepository,
    private val printerManager: PrinterPort9100Manager,
    private val posterBuilder: TableQrPosterBuilder = TableQrPosterBuilder(),
    private val tentCardRenderer: TableTentCardPdfRenderer = TableTentCardPdfRenderer(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle()
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        TableManagementUiState(selectedTableId = savedStateHandle.get<String>(KEY_SELECTED_TABLE))
    )
    val uiState: StateFlow<TableManagementUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<TableManagementUiEffect>()
    val effects: SharedFlow<TableManagementUiEffect> = _effects.asSharedFlow()

    init {
        refresh()
    }

    /** Reloads the board; clears the selection when the selected table is gone (archived elsewhere). */
    fun refresh() {
        viewModelScope.launch {
            val session = sessionManager.currentSession()
            if (session?.staff == null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        canManageTables = false,
                        canManageQr = false,
                        tables = emptyList(),
                        loadError = TableMessage.Res(R.string.tables_error_no_session)
                    )
                }
                return@launch
            }
            _uiState.update {
                it.copy(
                    isLoading = true,
                    loadError = null,
                    serverBaseUrl = serverSettings.getBaseUrl(),
                    canManageTables = PERMISSION_TABLE_MANAGE in session.permissions,
                    canManageQr = PERMISSION_TABLE_QR_MANAGE in session.permissions
                )
            }
            repository.listTables().fold(
                onSuccess = { tables -> applyTables(tables) },
                onFailure = { err ->
                    _uiState.update { it.copy(isLoading = false, loadError = err.toTableMessage(R.string.tables_error_load)) }
                }
            )
        }
    }

    fun selectTable(id: String) {
        savedStateHandle[KEY_SELECTED_TABLE] = id
        _uiState.update { it.copy(selectedTableId = id) }
    }

    /** Creates a table when [editingId] is null, otherwise updates it. */
    fun saveTable(editingId: String?, draft: TableDraft) {
        if (!_uiState.value.canManageTables || _uiState.value.isBusy) return
        if (draft.tableNumber.isBlank()) {
            emit(TableMessage.Res(R.string.tables_error_number_required), CassyToastTone.WARNING)
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            val result = if (editingId == null) repository.createTable(draft) else repository.updateTable(editingId, draft)
            result.fold(
                onSuccess = { saved ->
                    _uiState.update { state ->
                        val others = state.tables.filterNot { it.id == saved.id }
                        val merged = state.tables.firstOrNull { it.id == saved.id }
                            ?.let { old -> saved.copy(status = old.status, hasOpenSession = old.hasOpenSession, mergedIntoTableId = old.mergedIntoTableId, unpaidOrders = old.unpaidOrders) }
                            ?: saved
                        state.copy(isSubmitting = false, tables = (others + merged).sortedBy { it.tableNumber.lowercase() })
                    }
                    selectTable(saved.id)
                    _effects.emit(TableManagementUiEffect.FormSaved)
                    emit(TableMessage.Res(R.string.tables_msg_saved, saved.displayName), CassyToastTone.SUCCESS)
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isSubmitting = false) }
                    emit(err.toTableMessage(R.string.tables_error_save), CassyToastTone.ERROR)
                }
            )
        }
    }

    /** Archives a table. The backend refuses with `TABLE_SESSION_OPEN` while guests are seated. */
    fun archiveTable(id: String) {
        if (!_uiState.value.canManageTables || _uiState.value.isBusy) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            repository.archiveTable(id).fold(
                onSuccess = {
                    _uiState.update { state ->
                        state.copy(
                            isSubmitting = false,
                            tables = state.tables.filterNot { it.id == id },
                            selectedTableId = state.selectedTableId.takeUnless { it == id }
                        )
                    }
                    if (savedStateHandle.get<String>(KEY_SELECTED_TABLE) == id) savedStateHandle.remove<String>(KEY_SELECTED_TABLE)
                    emit(TableMessage.Res(R.string.tables_msg_archived), CassyToastTone.SUCCESS)
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isSubmitting = false) }
                    emit(err.toTableMessage(R.string.tables_error_archive), CassyToastTone.ERROR)
                }
            )
        }
    }

    /**
     * Rotates the QR of [id] after a fresh, fail-closed safety check (see class doc). The cached board
     * is never trusted for this decision: a guest may have scanned since it loaded.
     */
    fun confirmRotateQr(id: String) {
        if (!_uiState.value.canManageQr || _uiState.value.isBusy) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            val fresh = repository.listTables().getOrNull()
            if (fresh != null) applyTables(fresh)
            val freshTable = fresh?.firstOrNull { it.id == id }
            val block = if (freshTable == null) QrRotationBlock.STATE_UNKNOWN else freshTable.qrRotationBlock()
            if (block != null) {
                _uiState.update { it.copy(isSubmitting = false) }
                emit(TableMessage.Res(block.messageRes()), CassyToastTone.WARNING)
                return@launch
            }
            repository.rotateQr(id).fold(
                onSuccess = { rotated ->
                    _uiState.update { state ->
                        state.copy(
                            isSubmitting = false,
                            tables = state.tables.map { if (it.id == id) it.copy(qrToken = rotated.qrToken, qrRotatedAt = rotated.qrRotatedAt) else it }
                        )
                    }
                    emit(TableMessage.Res(R.string.tables_msg_qr_rotated, rotated.displayName), CassyToastTone.SUCCESS)
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isSubmitting = false) }
                    emit(err.toTableMessage(R.string.tables_error_rotate), CassyToastTone.ERROR)
                }
            )
        }
    }

    /** Prints the poster of [id] on the cashier printer (80 mm ESC/POS, port 9100, 2.5 s timeout). */
    fun printPoster(id: String, copy: TableQrPosterCopy) {
        if (_uiState.value.isBusy) return
        val table = _uiState.value.tables.firstOrNull { it.id == id } ?: return
        viewModelScope.launch {
            val url = qrUrlFor(table) ?: return@launch
            val printer = printerSettings.getConfig()
            if (!printer.hasCashierPrinter) {
                emit(TableMessage.Res(R.string.tables_error_no_printer), CassyToastTone.WARNING)
                return@launch
            }
            _uiState.update { it.copy(isPrinting = true) }
            val bytes = runCatching { posterBuilder.build(storeName(), table.displayName, table.section, url, copy) }
            val printed = bytes.fold(
                onSuccess = { printerManager.printRawBytes(printer.cashierPrinterIp, it, printer.cashierPrinterPort) },
                onFailure = { Result.failure(it) }
            )
            _uiState.update { it.copy(isPrinting = false) }
            if (printed.isSuccess) {
                emit(TableMessage.Res(R.string.tables_msg_poster_printed, table.displayName), CassyToastTone.SUCCESS)
            } else {
                emit(TableMessage.Res(R.string.tables_error_print), CassyToastTone.ERROR)
            }
        }
    }

    /**
     * Writes an A6 tent-card PDF for [id] to the stream from [openOutput] (a document the user picked).
     * The stream is closed here. [heading] and [scanHint] come from string resources.
     */
    fun exportTentCard(id: String, heading: String, scanHint: String, openOutput: () -> OutputStream?) {
        if (_uiState.value.isBusy) return
        val table = _uiState.value.tables.firstOrNull { it.id == id } ?: return
        viewModelScope.launch {
            val url = qrUrlFor(table) ?: return@launch
            _uiState.update { it.copy(isExporting = true) }
            val card = TentCardContent(
                storeName = storeName(),
                heading = heading,
                tableName = table.displayName,
                section = table.section,
                scanHint = scanHint,
                qrUrl = url
            )
            val written = withContext(ioDispatcher) {
                runCatching {
                    val stream = requireNotNull(openOutput()) { "No output stream" }
                    stream.use { tentCardRenderer.render(listOf(card), it) }
                }
            }
            _uiState.update { it.copy(isExporting = false) }
            if (written.isSuccess) {
                emit(TableMessage.Res(R.string.tables_msg_pdf_saved, table.displayName), CassyToastTone.SUCCESS)
            } else {
                emit(TableMessage.Res(R.string.tables_error_pdf), CassyToastTone.ERROR)
            }
        }
    }

    /** Guest URL for [table], or null after telling the user why no QR can be produced. */
    private suspend fun qrUrlFor(table: TableEntry): String? {
        if (!_uiState.value.canManageQr || table.qrToken == null) {
            emit(TableMessage.Res(R.string.tables_error_qr_hidden), CassyToastTone.WARNING)
            return null
        }
        val url = TableQrUrl.build(serverSettings.getBaseUrl(), table.qrToken)
        if (url == null) emit(TableMessage.Res(R.string.tables_error_server_url), CassyToastTone.ERROR)
        return url
    }

    private fun storeName(): String = sessionManager.currentSession()?.staff?.storeName.orEmpty()

    private fun applyTables(tables: List<TableEntry>) {
        _uiState.update { state ->
            val selected = state.selectedTableId?.takeIf { id -> tables.any { it.id == id } }
            state.copy(isLoading = false, loadError = null, tables = tables, selectedTableId = selected)
        }
    }

    private fun emit(message: TableMessage, tone: CassyToastTone) {
        viewModelScope.launch { _effects.emit(TableManagementUiEffect.ShowMessage(message, tone)) }
    }

    /** Backend and network errors already carry user-facing text; anything else gets [fallback]. */
    private fun Throwable.toTableMessage(@StringRes fallback: Int): TableMessage =
        (this as? DomainException)?.error?.message?.takeIf { it.isNotBlank() }?.let { TableMessage.Server(it) }
            ?: TableMessage.Res(fallback)

    private companion object {
        const val KEY_SELECTED_TABLE = "tables_selected_id"
        const val PERMISSION_TABLE_MANAGE = "table.manage"
        const val PERMISSION_TABLE_QR_MANAGE = "table.qr.manage"
    }
}

@StringRes
private fun QrRotationBlock.messageRes(): Int = when (this) {
    QrRotationBlock.ACTIVE_SESSION -> R.string.tables_block_active_session
    QrRotationBlock.MERGED -> R.string.tables_block_merged
    QrRotationBlock.UNPAID_ORDERS -> R.string.tables_block_unpaid
    QrRotationBlock.STATE_UNKNOWN -> R.string.tables_block_unknown
}
