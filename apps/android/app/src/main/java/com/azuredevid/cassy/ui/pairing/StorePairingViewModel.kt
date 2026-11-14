package com.azuredevid.cassy.ui.pairing

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.azuredevid.cassy.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Immutable
data class StorePairingUiState(
    val storeSlug: String = "",
    val email: String = "",
    val badge: String = "",
    val pin: String = "",
    val deviceCredential: String = "",
    val isCameraActive: Boolean = false,
    val isSubmitting: Boolean = false,
    val qrScannedInfo: String? = null,
    val storeSlugError: String? = null,
    val emailError: String? = null,
    val pinError: String? = null,
    val formError: String? = null
)

sealed interface StorePairingUiEvent {
    data class UpdateStoreSlug(val value: String) : StorePairingUiEvent
    data class UpdateEmail(val value: String) : StorePairingUiEvent
    data class UpdatePin(val value: String) : StorePairingUiEvent
    data class ToggleCamera(val active: Boolean) : StorePairingUiEvent
    data class HandleQrPayload(val rawPayload: String) : StorePairingUiEvent
    data object SubmitLogin : StorePairingUiEvent
}

sealed interface StorePairingUiEffect {
    data class PairingSucceeded(val storeSlug: String, val storeName: String) : StorePairingUiEffect
}

class StorePairingViewModel(
    private val authRepository: AuthRepository,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle()
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        StorePairingUiState(
            storeSlug = savedStateHandle.get<String>(KEY_STORE_SLUG).orEmpty(),
            email = savedStateHandle.get<String>(KEY_EMAIL).orEmpty()
        )
    )
    val uiState: StateFlow<StorePairingUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<StorePairingUiEffect>()
    val effects: SharedFlow<StorePairingUiEffect> = _effects.asSharedFlow()

    fun onEvent(event: StorePairingUiEvent) {
        when (event) {
            is StorePairingUiEvent.UpdateStoreSlug -> {
                savedStateHandle[KEY_STORE_SLUG] = event.value
                _uiState.update {
                    it.copy(storeSlug = event.value, storeSlugError = null, formError = null)
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
            is StorePairingUiEvent.ToggleCamera -> _uiState.update {
                it.copy(isCameraActive = event.active, formError = null)
            }
            is StorePairingUiEvent.HandleQrPayload -> parseQrPayload(event.rawPayload)
            StorePairingUiEvent.SubmitLogin -> submitLogin()
        }
    }

    private fun parseQrPayload(rawPayload: String) {
        val trimmed = rawPayload.trim()
        if (trimmed.isBlank()) {
            _uiState.update { it.copy(formError = ERR_QR_EMPTY) }
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
                val resolvedSlug = parsed.first.ifBlank { current.storeSlug }
                val resolvedEmail = parsed.second.first.ifBlank { current.email }
                savedStateHandle[KEY_STORE_SLUG] = resolvedSlug
                savedStateHandle[KEY_EMAIL] = resolvedEmail
                current.copy(
                    storeSlug = resolvedSlug,
                    email = resolvedEmail,
                    badge = parsed.second.second.ifBlank { current.badge },
                    deviceCredential = parsed.third.ifBlank { current.deviceCredential },
                    isCameraActive = false,
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

        if (slug.isBlank() && badge.isBlank()) {
            slugErr = ERR_SLUG_REQUIRED
        }
        if (email.isBlank() && badge.isBlank()) {
            emailErr = ERR_EMAIL_REQUIRED
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
                    _effects.emit(StorePairingUiEffect.PairingSucceeded(resolvedSlug, resolvedName))
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
        const val ERR_EMAIL_REQUIRED = "CODE_EMAIL_REQUIRED"
        const val ERR_PIN_INVALID = "CODE_PIN_INVALID"
        const val ERR_AUTH_FAILED = "CODE_AUTH_FAILED"
    }
}
