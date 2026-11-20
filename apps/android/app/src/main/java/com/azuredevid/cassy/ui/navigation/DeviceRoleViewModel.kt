package com.azuredevid.cassy.ui.navigation

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.azuredevid.cassy.domain.repository.DeviceProfileRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * [home] is the screen this tablet should open: KDS for a `kds` device, the cashier for every other role
 * until the backoffice screens exist. [held] keeps a cashier tablet on the cashier while its shift is
 * open or offline orders wait to sync, because the server cannot see either. [splitBillEnabled] is the
 * owner's `split_bill` flag; it stays on until the server says otherwise.
 */
@Immutable
data class DeviceRoleUiState(
    val home: DeviceHome = DeviceHome.POS,
    val held: Boolean = false,
    val splitBillEnabled: Boolean = true
)

enum class DeviceHome { POS, KDS }

/** RoleGate (PRD A2): reads the role the owner set for this device and decides where the app lives. */
class DeviceRoleViewModel(
    private val profiles: DeviceProfileRepository,
    private val hasOpenShift: suspend () -> Boolean,
    private val pendingOfflineOrders: suspend () -> Int,
    private val recheckMs: Long = 60_000L
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceRoleUiState())
    val uiState: StateFlow<DeviceRoleUiState> = _uiState.asStateFlow()

    private var job: Job? = null

    /** Called when the app comes to the foreground; a held switch keeps rechecking until it is clear. */
    fun refresh() {
        job?.cancel()
        job = viewModelScope.launch {
            while (true) {
                // Unpaired or unreachable: stay where the tablet already is.
                val profile = profiles.profile().getOrNull() ?: return@launch
                val splitBill = profile.features[FEATURE_SPLIT_BILL] ?: true
                if (profile.role != ROLE_KDS) {
                    _uiState.value = DeviceRoleUiState(DeviceHome.POS, splitBillEnabled = splitBill)
                    return@launch
                }
                val held = hasOpenShift() || pendingOfflineOrders() > 0
                _uiState.value = DeviceRoleUiState(DeviceHome.KDS, held, splitBill)
                if (!held) return@launch
                delay(recheckMs)
            }
        }
    }

    private companion object {
        const val ROLE_KDS = "kds"
        const val FEATURE_SPLIT_BILL = "split_bill"
    }
}
