package com.azuredevid.cassy.ui.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.azuredevid.cassy.BuildConfig
import com.azuredevid.cassy.data.repository.ServerSettingsRepository
import com.azuredevid.cassy.domain.repository.DiscoveredLocalServer
import com.azuredevid.cassy.domain.repository.LocalServerDiscoveryRepository
import com.azuredevid.cassy.domain.repository.ServerHealthMonitor
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import java.net.URI
import kotlin.time.TimeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

@Immutable
data class ServerConfigUiState(
    val urlInput: String = "",
    val urlError: String? = null,
    val isUrlInvalid: Boolean = false,
    val isCleartextBlockedInProd: Boolean = false,
    val isSaved: Boolean = false,
    val isTesting: Boolean = false,
    val probeStatusOk: Boolean? = null,
    val probeHttpCode: Int? = null,
    val probeLatencyMs: Long? = null,
    val probeIsTls: Boolean = false,
    val probeErrorMessage: String? = null,
    val showCleartextWarning: Boolean = false,
    val isDiscoveringMdns: Boolean = false,
    val discoveredServers: List<DiscoveredLocalServer> = emptyList(),
    val mdnsDiscoveryCompleted: Boolean = false
)

sealed interface ServerConfigUiEffect {
    data class ShowProbeResult(val success: Boolean, val detail: String) : ServerConfigUiEffect
}

class ServerConfigViewModel(
    private val serverSettings: ServerSettingsRepository,
    private val httpClient: HttpClient? = null,
    private val healthMonitor: ServerHealthMonitor? = null,
    private val localDiscovery: LocalServerDiscoveryRepository? = null,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    private val enforceHttpsOnly: Boolean = BuildConfig.FLAVOR.equals("prod", ignoreCase = true)
) : ViewModel() {

    private val initialUrl = savedStateHandle.get<String>(KEY_URL_INPUT)
        ?.takeIf { it.isNotBlank() }
        ?: serverSettings.getBaseUrl()

    private val _uiState = MutableStateFlow(
        ServerConfigUiState(
            urlInput = initialUrl,
            showCleartextWarning = isUnlistedCleartextUrl(initialUrl)
        )
    )
    val uiState: StateFlow<ServerConfigUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<ServerConfigUiEffect>(extraBufferCapacity = 4)
    val effects: SharedFlow<ServerConfigUiEffect> = _effects.asSharedFlow()

    fun onUrlChanged(value: String) {
        updateUrlState(value)
    }

    fun clearUrl() {
        updateUrlState("")
    }

    fun applyPreset(url: String) {
        updateUrlState(url)
    }

    fun discoverLocalServers() {
        val discovery = localDiscovery ?: return
        if (_uiState.value.isDiscoveringMdns) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isDiscoveringMdns = true,
                    mdnsDiscoveryCompleted = false
                )
            }
            val servers = runCatching { discovery.discoverLocalServers() }.getOrDefault(emptyList())
            val firstUrl = servers.firstOrNull()?.baseUrl
            _uiState.update { current ->
                val updatedUrl = firstUrl ?: current.urlInput
                if (firstUrl != null) {
                    savedStateHandle[KEY_URL_INPUT] = firstUrl
                }
                current.copy(
                    urlInput = updatedUrl,
                    isDiscoveringMdns = false,
                    discoveredServers = servers,
                    mdnsDiscoveryCompleted = true,
                    urlError = null,
                    isUrlInvalid = false,
                    isCleartextBlockedInProd = false,
                    showCleartextWarning = isUnlistedCleartextUrl(updatedUrl)
                )
            }
        }
    }

    private fun updateUrlState(value: String) {
        savedStateHandle[KEY_URL_INPUT] = value
        _uiState.update {
            it.copy(
                urlInput = value,
                urlError = null,
                isUrlInvalid = false,
                isCleartextBlockedInProd = false,
                isSaved = false,
                probeStatusOk = null,
                probeHttpCode = null,
                probeLatencyMs = null,
                probeIsTls = false,
                probeErrorMessage = null,
                showCleartextWarning = isUnlistedCleartextUrl(value)
            )
        }
    }

    fun testConnection() {
        val raw = _uiState.value.urlInput.trim().trimEnd('/')
        if (!isValidHttpUrl(raw)) {
            _uiState.update {
                it.copy(
                    isUrlInvalid = true,
                    isCleartextBlockedInProd = false,
                    urlError = ERR_INVALID_URL
                )
            }
            return
        }
        if (enforceHttpsOnly && raw.startsWith("http://", ignoreCase = true)) {
            _uiState.update {
                it.copy(
                    isUrlInvalid = true,
                    isCleartextBlockedInProd = true,
                    urlError = ERR_CLEARTEXT_PROD
                )
            }
            return
        }
        val client = httpClient ?: return
        val isTls = raw.startsWith("https://", ignoreCase = true)
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isTesting = true,
                    probeStatusOk = null,
                    probeHttpCode = null,
                    probeLatencyMs = null,
                    probeIsTls = isTls,
                    probeErrorMessage = null
                )
            }
            val mark = TimeSource.Monotonic.markNow()
            val result = try {
                val response = withTimeout(PROBE_TIMEOUT_MS) {
                    client.get("$raw/api/v1/health")
                }
                Result.success(response)
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                Result.failure(t)
            }
            val elapsedMs = mark.elapsedNow().inWholeMilliseconds.coerceAtLeast(1L)
            result.fold(
                onSuccess = { response ->
                    val ok = response.status.isSuccess()
                    _uiState.update {
                        it.copy(
                            isTesting = false,
                            probeStatusOk = ok,
                            probeHttpCode = response.status.value,
                            probeLatencyMs = elapsedMs,
                            probeIsTls = isTls,
                            probeErrorMessage = if (ok) null else "HTTP ${response.status.value}"
                        )
                    }
                    _effects.emit(
                        ServerConfigUiEffect.ShowProbeResult(
                            success = ok,
                            detail = "HTTP ${response.status.value} ($elapsedMs ms)"
                        )
                    )
                },
                onFailure = { err ->
                    val detail = err.message ?: "Network error"
                    _uiState.update {
                        it.copy(
                            isTesting = false,
                            probeStatusOk = false,
                            probeLatencyMs = null,
                            probeIsTls = isTls,
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
                    isCleartextBlockedInProd = false,
                    urlError = ERR_INVALID_URL
                )
            }
            return false
        }
        if (enforceHttpsOnly && raw.startsWith("http://", ignoreCase = true)) {
            _uiState.update {
                it.copy(
                    isUrlInvalid = true,
                    isCleartextBlockedInProd = true,
                    urlError = ERR_CLEARTEXT_PROD
                )
            }
            return false
        }
        serverSettings.setBaseUrl(raw)
        val normalized = serverSettings.getBaseUrl()
        savedStateHandle[KEY_URL_INPUT] = normalized
        healthMonitor?.requestProbe()
        _uiState.update {
            it.copy(
                urlInput = normalized,
                urlError = null,
                isUrlInvalid = false,
                isCleartextBlockedInProd = false,
                isSaved = true,
                showCleartextWarning = isUnlistedCleartextUrl(normalized)
            )
        }
        return true
    }

    private fun isValidHttpUrl(raw: String): Boolean {
        val trimmed = raw.trim()
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme != "http" && scheme != "https") return false
        val host = uri.host?.trim().orEmpty()
        if (host.isEmpty() || host.contains(' ')) return false
        if (!host.equals("localhost", ignoreCase = true) && !host.contains('.')) return false
        return uri.port == -1 || uri.port in 1..65535
    }

    private fun isUnlistedCleartextUrl(raw: String): Boolean {
        val uri = runCatching { URI(raw.trim()) }.getOrNull() ?: return false
        if (!uri.scheme.equals("http", ignoreCase = true)) return false
        val host = uri.host?.lowercase() ?: return false
        return host !in ALLOWED_CLEARTEXT_HOSTS
    }

    companion object {
        private const val KEY_URL_INPUT = "server_config_url_input"
        const val ERR_INVALID_URL = "URL_INVALID"
        const val ERR_CLEARTEXT_PROD = "URL_CLEARTEXT_PROD"
        private const val PROBE_TIMEOUT_MS = 4_500L
        private val ALLOWED_CLEARTEXT_HOSTS = setOf("10.0.2.2", "127.0.0.1", "localhost", "192.168.10.10")
    }
}
