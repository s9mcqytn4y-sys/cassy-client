package com.azuredevid.cassy.ui.shift

import androidx.compose.runtime.Immutable
import com.azuredevid.cassy.domain.model.CashDrawer
import com.azuredevid.cassy.domain.model.CashMovementType
import com.azuredevid.cassy.domain.model.Shift
import com.azuredevid.cassy.domain.model.XReport
import com.azuredevid.cassy.domain.model.ZReport
import com.azuredevid.cassy.ui.designsystem.CassyToastTone
import com.azuredevid.cassy.ui.designsystem.UiText

@Immutable
data class ShiftUiState(
    val isLoading: Boolean = false,
    val drawers: List<CashDrawer> = emptyList(),
    val selectedDrawerId: String? = null,
    val currentShift: Shift? = null,
    val isShiftOpen: Boolean = false,
    val errorMessage: UiText? = null,
    val successMessage: UiText? = null,

    // Modal controls
    val showOpenShiftModal: Boolean = false,
    val showCloseShiftModal: Boolean = false,
    val showCashMovementModal: Boolean = false,
    val showXReportModal: Boolean = false,

    // Open shift form
    val openingFloatInput: String = "200000",
    val openShiftNotes: String = "",
    val isOpeningShift: Boolean = false,

    // Cash movement form
    val movementType: CashMovementType = CashMovementType.PAID_OUT,
    val movementAmountInput: String = "",
    val movementReason: String = "",
    val isRecordingMovement: Boolean = false,

    // Close shift form
    val countedCashInput: String = "",
    val varianceReason: String = "",
    val closeShiftNotes: String = "",
    val supervisorPinInput: String = "",
    val printReceiptOnClose: Boolean = true,
    val calculatedVariance: Long? = null,
    val isSupervisorAuthRequired: Boolean = false,
    val isClosingShift: Boolean = false,

    // Reports & Printing
    val currentXReport: XReport? = null,
    val isLoadingXReport: Boolean = false,
    val isPrintingReport: Boolean = false,
    val lastZReport: ZReport? = null
)

sealed interface ShiftUiEffect {
    data class ShowToast(
        val message: UiText,
        val tone: CassyToastTone = CassyToastTone.INFO
    ) : ShiftUiEffect
    data object PromptOpenShift : ShiftUiEffect
    data class ShiftOpenedSuccessfully(val shift: Shift) : ShiftUiEffect
    data class ShiftClosedSuccessfully(val report: ZReport) : ShiftUiEffect
}
