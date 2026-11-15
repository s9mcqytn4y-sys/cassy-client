package com.azuredevid.cassy.ui.pairing

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.azuredevid.cassy.domain.repository.AuthRepository
import com.azuredevid.cassy.domain.repository.ServerConnectionStatus
import com.azuredevid.cassy.domain.repository.ServerConnectivityState
import com.azuredevid.cassy.domain.repository.ServerHealthMonitor
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class StorePairingUiState(
    val storeSlug: String = "",
    val email: String = "",
    val badge: String = "",
    val pin: String = "",
    val deviceCredential: String = "",
    val isPinVisible: Boolean = false,
    val isCameraActive: Boolean = false,
    val isHidScannerOpen: Boolean = false,
    val hidScannerInput: String = "",
    val cameraPermissionDenied: Boolean = false,
    val isSubmitting: Boolean = false,
    val qrScannedInfo: String? = null,
    val storeSlugError: String? = null,
    val emailError: String? = null,
    val pinError: String? = null,
    val formError: String? = null
) {
    val isStoreSlugValid: Boolean
        get() = storeSlug.trim().lowercase().let { SLUG_REGEX.matches(it) }

    val isEmailValid: Boolean
        get() = email.trim().let { EMAIL_REGEX.matches(it) }

    val isPinValid: Boolean
        get() = pin.length == 6 && pin.all { it.isDigit() }

    companion object {
        private val SLUG_REGEX = Regex("^[a-z0-9][a-z0-9-]{1,62}$")
        private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    }
}

sealed interface StorePairingUiEvent {
    data class UpdateStoreSlug(val value: String) : StorePairingUiEvent
    data class UpdateEmail(val value: String) : StorePairingUiEvent
    data class UpdatePin(val value: String) : StorePairingUiEvent
    data object TogglePinVisibility : StorePairingUiEvent
    data class ToggleCamera(val active: Boolean) : StorePairingUiEvent
    data object CameraPermissionDenied : StorePairingUiEvent
    data object ClearCameraPermissionDenied : StorePairingUiEvent
    data object ClearQrScanned : StorePairingUiEvent
    data class OpenHidScanner(val open: Boolean) : StorePairingUiEvent
    data class UpdateHidInput(val value: String) : StorePairingUiEvent
    data object SubmitHidQrPayload : StorePairingUiEvent
    data class HandleQrPayload(val rawPayload: String) : StorePairingUiEvent
    data object RetryServerHealthProbe : StorePairingUiEvent
    data object SubmitLogin : StorePairingUiEvent
}

sealed interface StorePairingUiEffect {
    data class PairingSucceeded(val storeSlug: String, val storeName: String) : StorePairingUiEffect
}

private object NoOpServerHealthMonitor : ServerHealthMonitor {
    override fun observeConnectivity(): Flow<ServerConnectivityState> =
        flowOf(ServerConnectivityState(ServerConnectionStatus.ONLINE))

    override fun requestProbe() = Unit

    override suspend fun probeNow(): ServerConnectivityState =
        ServerConnectivityState(ServerConnectionStatus.ONLINE)
}

class StorePairingViewModel(
    private val authRepository: AuthRepository,
    private val healthMonitor: ServerHealthMonitor = NoOpServerHealthMonitor,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle()
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        StorePairingUiState(
            storeSlug = savedStateHandle.get<String>(KEY_STORE_SLUG).orEmpty(),
            email = savedStateHandle.get<String>(KEY_EMAIL).orEmpty()
        )
    )
    val uiState: StateFlow<StorePairingUiState> = _uiState.asStateFlow()

    val connectivityState: StateFlow<ServerConnectivityState> =
        healthMonitor.observeConnectivity()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = ServerConnectivityState(status = ServerConnectionStatus.CHECKING)
            )

    private val _effects = Channel<StorePairingUiEffect>(Channel.BUFFERED)
    val effects: Flow<StorePairingUiEffect> = _effects.receiveAsFlow()

    fun onEvent(event: StorePairingUiEvent) {
        when (event) {
            is StorePairingUiEvent.UpdateStoreSlug -> {
                val sanitized = event.value.lowercase().filter { ch ->
                    ch in 'a'..'z' || ch in '0'..'9' || ch == '-'
                }
                savedStateHandle[KEY_STORE_SLUG] = sanitized
                _uiState.update {
                    it.copy(storeSlug = sanitized, storeSlugError = null, formError = null)
                }
            }
            is StorePairingUiEvent.UpdateEmail -> {
                savedStateHandle[KEY_EMAIL] = event.value
                _uiState.update {
                    it.copy(email = event.value, emailError = null, formError = null)
                }
            }
            is StorePairingUiEvent.UpdatePin -> {
                val digits = event.value.filter { ch -> ch.isDigit() }.take(6)
                _uiState.update { it.copy(pin = digits, pinError = null, formError = null) }
            }
            StorePairingUiEvent.TogglePinVisibility -> _uiState.update {
                it.copy(isPinVisible = !it.isPinVisible)
            }
            is StorePairingUiEvent.ToggleCamera -> _uiState.update {
                it.copy(
                    isCameraActive = event.active,
                    cameraPermissionDenied = if (event.active) false else it.cameraPermissionDenied,
                    formError = null
                )
            }
            StorePairingUiEvent.CameraPermissionDenied -> _uiState.update {
                it.copy(isCameraActive = false, cameraPermissionDenied = true)
            }
            StorePairingUiEvent.ClearCameraPermissionDenied -> _uiState.update {
                it.copy(cameraPermissionDenied = false)
            }
            StorePairingUiEvent.ClearQrScanned -> _uiState.update {
                it.copy(qrScannedInfo = null, badge = "", deviceCredential = "")
            }
            is StorePairingUiEvent.OpenHidScanner -> _uiState.update {
                it.copy(
                    isHidScannerOpen = event.open,
                    isCameraActive = if (event.open) false else it.isCameraActive,
                    hidScannerInput = if (!event.open) "" else it.hidScannerInput,
                    formError = null
                )
            }
            is StorePairingUiEvent.UpdateHidInput -> _uiState.update {
                it.copy(hidScannerInput = event.value)
            }
            StorePairingUiEvent.SubmitHidQrPayload -> parseQrPayload(_uiState.value.hidScannerInput)
            is StorePairingUiEvent.HandleQrPayload -> parseQrPayload(event.rawPayload)
            StorePairingUiEvent.RetryServerHealthProbe -> healthMonitor.requestProbe()
            StorePairingUiEvent.SubmitLogin -> submitLogin()
        }
    }

    private fun parseQrPayload(rawPayload: String) {
        val trimmed = rawPayload.trim()
        if (trimmed.isBlank()) {
            _uiState.update {
                it.copy(
                    isCameraActive = false,
                    isHidScannerOpen = false,
                    hidScannerInput = "",
                    formError = ERR_QR_EMPTY
                )
            }
            return
        }

        val parsed = runCatching {
            val json = Json.parseToJsonElement(trimmed).jsonObject
            val slug = json.stringField("store_slug", "storeSlug")
            val email = json.stringField("email")
            val badge = json.stringField("badge")
            val deviceCred = json.stringField("device_credential", "pairing_token")
            Triple(slug, Pair(email, badge), deviceCred)
        }.getOrNull()

        if (parsed != null && (parsed.first.isNotBlank() || parsed.third.isNotBlank() || parsed.second.second.isNotBlank())) {
            _uiState.update { current ->
                val resolvedSlug = parsed.first.lowercase().ifBlank { current.storeSlug }
                val resolvedEmail = parsed.second.first.ifBlank { current.email }
                savedStateHandle[KEY_STORE_SLUG] = resolvedSlug
                savedStateHandle[KEY_EMAIL] = resolvedEmail
                current.copy(
                    storeSlug = resolvedSlug,
                    email = resolvedEmail,
                    badge = parsed.second.second.ifBlank { current.badge },
                    deviceCredential = parsed.third.ifBlank { current.deviceCredential },
                    isCameraActive = false,
                    isHidScannerOpen = false,
                    hidScannerInput = "",
                    cameraPermissionDenied = false,
                    qrScannedInfo = INFO_QR_SCANNED,
                    storeSlugError = null,
                    emailError = null,
                    formError = null
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    isCameraActive = false,
                    isHidScannerOpen = false,
                    hidScannerInput = "",
                    formError = ERR_QR_FORMAT
                )
            }
        }
    }

    private fun submitLogin() {
        val state = _uiState.value
        val slug = state.storeSlug.trim().lowercase()
        val email = state.email.trim()
        val badge = state.badge.trim()
        val pin = state.pin.trim()

        var slugErr: String? = null
        var emailErr: String? = null
        var pinErr: String? = null

        if (badge.isBlank()) {
            if (slug.isBlank()) {
                slugErr = ERR_SLUG_REQUIRED
            } else if (!state.isStoreSlugValid) {
                slugErr = ERR_SLUG_INVALID
            }

            if (email.isBlank()) {
                emailErr = ERR_EMAIL_REQUIRED
            } else if (!state.isEmailValid) {
                emailErr = ERR_EMAIL_INVALID
            }
        }

        if (pin.length != 6 || !pin.all { it.isDigit() }) {
            pinErr = ERR_PIN_INVALID
        }

        if (slugErr != null || emailErr != null || pinErr != null) {
            _uiState.update {
                it.copy(
                    storeSlugError = slugErr,
                    emailError = emailErr,
                    pinError = pinErr
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, formError = null) }
            val loginResult = authRepository.login(
                storeSlug = slug,
                email = email,
                badge = badge,
                pin = pin,
                deviceCredential = state.deviceCredential.ifBlank { null }
            )

            loginResult.fold(
                onSuccess = { auth ->
                    val bootstrap = authRepository.fetchMobileBootstrap()
                    val resolvedSlug = bootstrap.getOrNull()?.storeSlug?.ifBlank { auth.staff.storeSlug }
                        ?: auth.staff.storeSlug
                    val resolvedName = bootstrap.getOrNull()?.storeName?.ifBlank { auth.staff.storeName }
                        ?: auth.staff.storeName

                    _uiState.update { it.copy(isSubmitting = false, pin = "") }
                    _effects.send(StorePairingUiEffect.PairingSucceeded(resolvedSlug, resolvedName))
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            pin = "",
                            formError = err.message ?: ERR_AUTH_FAILED
                        )
                    }
                }
            )
        }
    }

    private fun JsonObject.stringField(vararg keys: String): String {
        for (key in keys) {
            val raw = this[key]?.jsonPrimitive?.contentOrNull?.trim()
            if (!raw.isNullOrEmpty()) return raw
        }
        return ""
    }

    companion object {
        private const val KEY_STORE_SLUG = "pairing_store_slug"
        private const val KEY_EMAIL = "pairing_email"
        const val ERR_QR_EMPTY = "CODE_QR_EMPTY"
        const val ERR_QR_FORMAT = "CODE_QR_INVALID"
        const val INFO_QR_SCANNED = "CODE_QR_SCANNED"
        const val ERR_SLUG_REQUIRED = "CODE_SLUG_REQUIRED"
        const val ERR_SLUG_INVALID = "CODE_SLUG_INVALID"
        const val ERR_EMAIL_REQUIRED = "CODE_EMAIL_REQUIRED"
        const val ERR_EMAIL_INVALID = "CODE_EMAIL_INVALID"
        const val ERR_PIN_INVALID = "CODE_PIN_INVALID"
        const val ERR_AUTH_FAILED = "CODE_AUTH_FAILED"
    }
}
