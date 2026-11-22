package com.azuredevid.cassy.ui.shift

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.azuredevid.cassy.R
import com.azuredevid.cassy.core.hardware.BluetoothPrinterClient
import com.azuredevid.cassy.core.hardware.PrinterPort9100Manager
import com.azuredevid.cassy.core.hardware.ShiftReportThermalBuilder
import com.azuredevid.cassy.core.security.SessionManager
import com.azuredevid.cassy.domain.error.DomainError
import com.azuredevid.cassy.domain.error.DomainException
import com.azuredevid.cassy.domain.model.CashMovementType
import com.azuredevid.cassy.domain.model.ShiftStatus
import com.azuredevid.cassy.domain.model.ZReport
import com.azuredevid.cassy.domain.repository.PrinterSettingsRepository
import com.azuredevid.cassy.domain.repository.ShiftRepository
import com.azuredevid.cassy.ui.designsystem.UiText
import com.azuredevid.cassy.ui.designsystem.toUiText
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs

class ShiftViewModel(
    private val shiftRepository: ShiftRepository,
    private val printerSettings: PrinterSettingsRepository,
    private val sessionManager: SessionManager? = null,
    private val printerManager: PrinterPort9100Manager = PrinterPort9100Manager(),
    private val thermalBuilder: ShiftReportThermalBuilder = ShiftReportThermalBuilder(),
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    private val bluetoothPrinter: BluetoothPrinterClient? = null
) : ViewModel() {

    private val reportPrinter = ShiftReportPrinter(
        printerSettings = printerSettings,
        sessionManager = sessionManager,
        printerManager = printerManager,
        thermalBuilder = thermalBuilder,
        bluetoothPrinter = bluetoothPrinter
    )

    private val _uiState = MutableStateFlow(
        ShiftUiState(selectedDrawerId = savedStateHandle.get<String>(KEY_SELECTED_DRAWER))
    )
    val uiState: StateFlow<ShiftUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<ShiftUiEffect>()
    val effects: SharedFlow<ShiftUiEffect> = _effects.asSharedFlow()

    companion object {
        const val VARIANCE_TOLERANCE_RUPIAH = 50_000L
        private const val CODE_VARIANCE_EXCEEDS_TOLERANCE = "VARIANCE_EXCEEDS_TOLERANCE"
        private const val KEY_SELECTED_DRAWER = "shift_selected_drawer"
    }

    init {
        loadDrawersAndCurrentShift()
    }

    fun loadDrawersAndCurrentShift() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val drawers = shiftRepository.getDrawers().getOrDefault(emptyList())
            val selectedId = _uiState.value.selectedDrawerId
                ?.takeIf { id -> drawers.any { it.id == id } }
                ?: drawers.firstOrNull()?.id

            _uiState.update { it.copy(drawers = drawers, selectedDrawerId = selectedId) }
            fetchCurrentShiftInternal(selectedId)
        }
    }

    fun loadCurrentShift() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            fetchCurrentShiftInternal(_uiState.value.selectedDrawerId)
        }
    }

    private suspend fun fetchCurrentShiftInternal(drawerId: String?) {
        shiftRepository.getCurrentShift(drawerId)
            .onSuccess { shift ->
                val isOpen = shift != null && shift.status == ShiftStatus.OPEN
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        currentShift = shift,
                        isShiftOpen = isOpen,
                        selectedDrawerId = shift?.drawerId ?: it.selectedDrawerId
                    )
                }
            }
            .onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.toUiText(R.string.shift_msg_check_failed)
                    )
                }
            }
    }

    fun onDrawerSelected(drawerId: String) {
        savedStateHandle[KEY_SELECTED_DRAWER] = drawerId
        _uiState.update { it.copy(selectedDrawerId = drawerId) }
        loadCurrentShift()
    }

    fun openShiftModal() {
        viewModelScope.launch {
            if (_uiState.value.drawers.isEmpty()) {
                val drawers = shiftRepository.getDrawers().getOrDefault(emptyList())
                _uiState.update {
                    it.copy(
                        drawers = drawers,
                        selectedDrawerId = it.selectedDrawerId ?: drawers.firstOrNull()?.id
                    )
                }
            }
            _uiState.update {
                it.copy(
                    showOpenShiftModal = true,
                    openingFloatInput = "200000",
                    openShiftNotes = "",
                    errorMessage = null
                )
            }
        }
    }

    fun closeOpenShiftModal() {
        _uiState.update { it.copy(showOpenShiftModal = false) }
    }

    fun onOpeningFloatChanged(input: String) {
        val sanitized = input.filter { it.isDigit() }
        _uiState.update { it.copy(openingFloatInput = sanitized) }
    }

    fun onOpenShiftNotesChanged(notes: String) {
        _uiState.update { it.copy(openShiftNotes = notes) }
    }

    fun submitOpenShift() {
        val amount = _uiState.value.openingFloatInput.toLongOrNull() ?: 0L
        if (amount < 0L) {
            _uiState.update { it.copy(errorMessage = UiText.res(R.string.shift_msg_float_negative)) }
            return
        }

        val drawerId = _uiState.value.selectedDrawerId
        val notes = _uiState.value.openShiftNotes

        viewModelScope.launch {
            _uiState.update { it.copy(isOpeningShift = true, errorMessage = null) }
            shiftRepository.openShift(
                openingFloat = amount,
                drawerId = drawerId,
                notes = notes
            ).onSuccess { result ->
                _uiState.update {
                    it.copy(
                        isOpeningShift = false,
                        showOpenShiftModal = false,
                        currentShift = result.shift,
                        isShiftOpen = result.shift.status == ShiftStatus.OPEN,
                        selectedDrawerId = result.shift.drawerId,
                        successMessage = UiText.res(R.string.shift_msg_opened_with_float, amount)
                    )
                }
                if (result.openDrawer) {
                    reportPrinter.kickDrawerIfConfigured { _effects.emit(it) }
                }
                _effects.emit(ShiftUiEffect.ShiftOpenedSuccessfully(result.shift))
                _effects.emit(ShiftUiEffect.ShowToast(UiText.res(R.string.shift_msg_opened_toast)))
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isOpeningShift = false,
                        errorMessage = error.toUiText(R.string.shift_msg_open_failed)
                    )
                }
            }
        }
    }

    fun openCashMovementModal() {
        if (!_uiState.value.isShiftOpen) {
            viewModelScope.launch {
                _effects.emit(ShiftUiEffect.ShowToast(UiText.res(R.string.shift_msg_open_first)))
                _effects.emit(ShiftUiEffect.PromptOpenShift)
            }
            return
        }
        _uiState.update {
            it.copy(
                showCashMovementModal = true,
                movementType = CashMovementType.PAID_OUT,
                movementAmountInput = "",
                movementReason = "",
                errorMessage = null
            )
        }
    }

    fun closeCashMovementModal() {
        _uiState.update { it.copy(showCashMovementModal = false) }
    }

    fun onMovementTypeChanged(type: CashMovementType) {
        _uiState.update { it.copy(movementType = type) }
    }

    fun onMovementAmountChanged(input: String) {
        val sanitized = input.filter { it.isDigit() }
        _uiState.update { it.copy(movementAmountInput = sanitized) }
    }

    fun onMovementReasonChanged(reason: String) {
        _uiState.update { it.copy(movementReason = reason) }
    }

    fun submitCashMovement() {
        val amount = _uiState.value.movementAmountInput.toLongOrNull() ?: 0L
        val reason = _uiState.value.movementReason.trim()

        if (amount <= 0L) {
            _uiState.update { it.copy(errorMessage = UiText.res(R.string.shift_msg_amount_positive)) }
            return
        }
        if (reason.isBlank()) {
            _uiState.update { it.copy(errorMessage = UiText.res(R.string.shift_msg_reason_required)) }
            return
        }

        val type = _uiState.value.movementType
        val shiftId = _uiState.value.currentShift?.id
        val drawerId = _uiState.value.currentShift?.drawerId ?: _uiState.value.selectedDrawerId

        viewModelScope.launch {
            _uiState.update { it.copy(isRecordingMovement = true, errorMessage = null) }
            shiftRepository.recordCashMovement(
                movementType = type,
                amount = amount,
                reason = reason,
                shiftId = shiftId,
                drawerId = drawerId
            ).onSuccess { result ->
                _uiState.update {
                    it.copy(
                        isRecordingMovement = false,
                        showCashMovementModal = false,
                        successMessage = UiText.res(R.string.shift_msg_movement_recorded)
                    )
                }
                if (result.openDrawer) {
                    reportPrinter.kickDrawerIfConfigured { _effects.emit(it) }
                }
                _effects.emit(ShiftUiEffect.ShowToast(UiText.res(R.string.shift_msg_movement_saved)))
                loadCurrentShift()
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isRecordingMovement = false,
                        errorMessage = error.toUiText(R.string.shift_msg_movement_failed)
                    )
                }
            }
        }
    }

    fun openXReportModal() {
        val shift = _uiState.value.currentShift
        if (!_uiState.value.isShiftOpen || shift == null) {
            viewModelScope.launch {
                _effects.emit(ShiftUiEffect.ShowToast(UiText.res(R.string.shift_msg_no_shift_xreport)))
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(showXReportModal = true, isLoadingXReport = true, errorMessage = null) }
            shiftRepository.getXReport(shift.id)
                .onSuccess { report ->
                    _uiState.update {
                        it.copy(
                            isLoadingXReport = false,
                            currentXReport = report
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoadingXReport = false,
                            showXReportModal = false,
                            errorMessage = error.toUiText(R.string.shift_msg_xreport_failed)
                        )
                    }
                    _effects.emit(ShiftUiEffect.ShowToast(error.toUiText(R.string.shift_msg_xreport_failed)))
                }
        }
    }

    fun closeXReportModal() {
        _uiState.update { it.copy(showXReportModal = false) }
    }

    fun printCurrentXReport() {
        val report = _uiState.value.currentXReport ?: return
        viewModelScope.launch {
            reportPrinter.printXReport(
                report = report,
                onPrintingStateChanged = { isPrinting -> _uiState.update { it.copy(isPrintingReport = isPrinting) } },
                onEffect = { _effects.emit(it) }
            )
        }
    }

    fun openCloseShiftModal() {
        if (!_uiState.value.isShiftOpen) {
            viewModelScope.launch {
                _effects.emit(ShiftUiEffect.ShowToast(UiText.res(R.string.shift_msg_no_shift_close)))
            }
            return
        }
        _uiState.update {
            it.copy(
                showCloseShiftModal = true,
                countedCashInput = "",
                varianceReason = "",
                closeShiftNotes = "",
                supervisorPinInput = "",
                calculatedVariance = null,
                isSupervisorAuthRequired = false,
                errorMessage = null
            )
        }
    }

    fun closeCloseShiftModal() {
        _uiState.update { it.copy(showCloseShiftModal = false) }
    }

    fun onCountedCashChanged(input: String) {
        val sanitized = input.filter { it.isDigit() }
        val counted = sanitized.toLongOrNull()
        val expected = _uiState.value.currentShift?.expectedCash

        // When expectedCash is null (blind count for cashier role), never synthesize variance vs 0.
        val variance = if (counted != null && expected != null) counted - expected else null
        val requiresSupervisor = variance != null && abs(variance) > VARIANCE_TOLERANCE_RUPIAH

        _uiState.update {
            it.copy(
                countedCashInput = sanitized,
                calculatedVariance = variance,
                isSupervisorAuthRequired = requiresSupervisor || it.isSupervisorAuthRequired
            )
        }
    }

    fun onVarianceReasonChanged(reason: String) {
        _uiState.update { it.copy(varianceReason = reason) }
    }

    fun onCloseShiftNotesChanged(notes: String) {
        _uiState.update { it.copy(closeShiftNotes = notes) }
    }

    fun onSupervisorPinChanged(pin: String) {
        val sanitized = pin.filter { it.isDigit() }.take(6)
        _uiState.update { it.copy(supervisorPinInput = sanitized) }
    }

    fun onPrintReceiptOnCloseToggled(enabled: Boolean) {
        _uiState.update { it.copy(printReceiptOnClose = enabled) }
    }

    fun submitCloseShift() {
        val counted = _uiState.value.countedCashInput.toLongOrNull()
        if (counted == null) {
            _uiState.update { it.copy(errorMessage = UiText.res(R.string.shift_msg_counted_required)) }
            return
        }

        val shift = _uiState.value.currentShift
        if (shift == null) {
            _uiState.update { it.copy(errorMessage = UiText.res(R.string.shift_msg_not_found)) }
            return
        }

        val isSupervisorRequired = _uiState.value.isSupervisorAuthRequired
        val supervisorPin = _uiState.value.supervisorPinInput
        val varianceReason = _uiState.value.varianceReason.trim()

        if (isSupervisorRequired) {
            if (varianceReason.isBlank()) {
                _uiState.update { it.copy(errorMessage = UiText.res(R.string.shift_msg_variance_reason)) }
                return
            }
            if (supervisorPin.length != 6) {
                _uiState.update { it.copy(errorMessage = UiText.res(R.string.shift_msg_owner_pin_required)) }
                return
            }
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isClosingShift = true, errorMessage = null) }
            val notes = _uiState.value.closeShiftNotes
            shiftRepository.closeShift(
                shiftId = shift.id,
                countedCash = counted,
                ownerPin = if (isSupervisorRequired || supervisorPin.length == 6) supervisorPin.ifBlank { null } else null,
                varianceReason = varianceReason.ifBlank { null },
                notes = notes
            ).onSuccess { result ->
                _uiState.update {
                    it.copy(
                        isClosingShift = false,
                        showCloseShiftModal = false,
                        currentShift = null,
                        isShiftOpen = false,
                        lastZReport = result.report,
                        successMessage = UiText.res(R.string.shift_msg_closed)
                    )
                }
                if (result.openDrawer) {
                    reportPrinter.kickDrawerIfConfigured { _effects.emit(it) }
                }
                _effects.emit(ShiftUiEffect.ShiftClosedSuccessfully(result.report))
                _effects.emit(ShiftUiEffect.ShowToast(UiText.res(R.string.shift_msg_closed)))

                if (_uiState.value.printReceiptOnClose) {
                    printZReport(result.report)
                }
            }.onFailure { error ->
                val isVarianceGate = error is DomainException &&
                    (error.error as? DomainError.Validation)?.code == CODE_VARIANCE_EXCEEDS_TOLERANCE
                _uiState.update {
                    it.copy(
                        isClosingShift = false,
                        isSupervisorAuthRequired = it.isSupervisorAuthRequired || isVarianceGate,
                        errorMessage = error.toUiText(R.string.shift_msg_close_failed)
                    )
                }
            }
        }
    }

    fun forceCloseCurrentShift(reason: String, countedCash: Long? = null, notes: String? = null) {
        val shift = _uiState.value.currentShift ?: return
        val cleanReason = reason.trim()
        if (cleanReason.isBlank()) {
            _uiState.update { it.copy(errorMessage = UiText.res(R.string.shift_msg_variance_reason)) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isClosingShift = true, errorMessage = null) }
            shiftRepository.forceCloseShift(
                shiftId = shift.id,
                reason = cleanReason,
                countedCash = countedCash,
                notes = notes
            ).onSuccess { report ->
                _uiState.update {
                    it.copy(
                        isClosingShift = false,
                        showCloseShiftModal = false,
                        currentShift = null,
                        isShiftOpen = false,
                        lastZReport = report,
                        successMessage = UiText.res(R.string.shift_msg_force_closed)
                    )
                }
                _effects.emit(ShiftUiEffect.ShiftClosedSuccessfully(report))
                _effects.emit(ShiftUiEffect.ShowToast(UiText.res(R.string.shift_msg_force_closed)))
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isClosingShift = false,
                        errorMessage = error.toUiText(R.string.shift_msg_force_close_failed)
                    )
                }
            }
        }
    }

    fun printZReport(report: ZReport) {
        viewModelScope.launch {
            reportPrinter.printZReport(
                report = report,
                onPrintingStateChanged = { isPrinting -> _uiState.update { it.copy(isPrintingReport = isPrinting) } },
                onEffect = { _effects.emit(it) }
            )
        }
    }

    fun clearFeedback() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }
}
