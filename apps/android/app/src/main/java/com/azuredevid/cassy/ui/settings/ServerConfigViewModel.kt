package com.azuredevid.cassy.ui.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.azuredevid.cassy.data.repository.ServerSettingsRepository
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class ServerConfigUiState(
    val urlInput: String = "",
    val urlError: String? = null,
    val isUrlInvalid: Boolean = false,
    val isSaved: Boolean = false,
    val isTesting: Boolean = false,
    val probeStatusOk: Boolean? = null,
    val probeHttpCode: Int? = null,
    val probeErrorMessage: String? = null
)

sealed interface ServerConfigUiEffect {
    data object SavedAndNavigateBack : ServerConfigUiEffect
    data class ShowProbeResult(val success: Boolean, val detail: String) : ServerConfigUiEffect
}

class ServerConfigViewModel(
    private val serverSettings: ServerSettingsRepository,
    private val httpClient: HttpClient? = null,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle()
) : ViewModel() {

    private val initialUrl = savedStateHandle.get<String>(KEY_URL_INPUT)
        ?.takeIf { it.isNotBlank() }
        ?: serverSettings.getBaseUrl()

    private val _uiState = MutableStateFlow(ServerConfigUiState(urlInput = initialUrl))
    val uiState: StateFlow<ServerConfigUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<ServerConfigUiEffect>()
    val effects: SharedFlow<ServerConfigUiEffect> = _effects.asSharedFlow()

    fun onUrlChanged(value: String) {
        savedStateHandle[KEY_URL_INPUT] = value
        _uiState.update {
            it.copy(
                urlInput = value,
                urlError = null,
                isUrlInvalid = false,
                isSaved = false,
                probeStatusOk = null,
                probeHttpCode = null,
                probeErrorMessage = null
            )
        }
    }

    fun applyPreset(url: String) {
        savedStateHandle[KEY_URL_INPUT] = url
        _uiState.update {
            it.copy(
                urlInput = url,
                urlError = null,
                isUrlInvalid = false,
                isSaved = false,
                probeStatusOk = null,
                probeHttpCode = null,
                probeErrorMessage = null
            )
        }
    }

    fun testConnection() {
        val raw = _uiState.value.urlInput.trim().trimEnd('/')
        if (!isValidHttpUrl(raw)) {
            _uiState.update {
                it.copy(
                    isUrlInvalid = true,
                    urlError = ERR_INVALID_URL
                )
            }
            return
        }
        val client = httpClient ?: return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isTesting = true,
                    probeStatusOk = null,
                    probeHttpCode = null,
                    probeErrorMessage = null
                )
            }
            val result = runCatching {
                client.get("$raw/api/v1/health")
            }
            result.fold(
                onSuccess = { response ->
                    val ok = response.status.isSuccess()
                    _uiState.update {
                        it.copy(
                            isTesting = false,
                            probeStatusOk = ok,
                            probeHttpCode = response.status.value,
                            probeErrorMessage = if (ok) null else "HTTP ${response.status.value}"
                        )
                    }
                    _effects.emit(
                        ServerConfigUiEffect.ShowProbeResult(
                            success = ok,
                            detail = "HTTP ${response.status.value}"
                        )
                    )
                },
                onFailure = { err ->
                    val detail = err.message ?: "Network error"
                    _uiState.update {
                        it.copy(
                            isTesting = false,
                            probeStatusOk = false,
                            probeErrorMessage = detail
                        )
                    }
                    _effects.emit(ServerConfigUiEffect.ShowProbeResult(success = false, detail = detail))
                }
            )
        }
    }

    fun save(): Boolean {
        val raw = _uiState.value.urlInput.trim()
        if (!isValidHttpUrl(raw)) {
            _uiState.update {
                it.copy(
                    isUrlInvalid = true,
                    urlError = ERR_INVALID_URL
                )
            }
            return false
        }
        serverSettings.setBaseUrl(raw)
        val normalized = serverSettings.getBaseUrl()
        savedStateHandle[KEY_URL_INPUT] = normalized
        _uiState.update {
            it.copy(
                urlInput = normalized,
                urlError = null,
                isUrlInvalid = false,
                isSaved = true
            )
        }
        return true
    }

    private fun isValidHttpUrl(raw: String): Boolean {
        val validScheme = raw.startsWith("https://", ignoreCase = true) ||
            raw.startsWith("http://", ignoreCase = true)
        return validScheme && raw.length >= 10
    }

    companion object {
        private const val KEY_URL_INPUT = "server_config_url_input"
        private const val ERR_INVALID_URL = "URL_INVALID"
    }
}
