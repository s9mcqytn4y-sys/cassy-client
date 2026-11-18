package com.azuredevid.cassy.ui.settings

import com.azuredevid.cassy.ui.designsystem.toUiText
import com.azuredevid.cassy.ui.designsystem.UiText
import com.azuredevid.cassy.R
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
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
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
    val probeErrorMessage: UiText? = null,
    val lastVerifiedEndpoint: String? = null,
    val showCleartextWarning: Boolean = false,
    val isDiscoveringMdns: Boolean = false,
    val showMdnsDialog: Boolean = false,
    val discoveredServers: List<DiscoveredLocalServer> = emptyList(),
    val mdnsDiscoveryCompleted: Boolean = false,
    val enforceHttpsOnly: Boolean = false
)

sealed interface ServerConfigUiEffect {
    data class ShowProbeResult(val success: Boolean, val detail: UiText) : ServerConfigUiEffect
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
            showCleartextWarning = isUnlistedCleartextUrl(initialUrl),
            enforceHttpsOnly = enforceHttpsOnly
        )
    )
    val uiState: StateFlow<ServerConfigUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<ServerConfigUiEffect>(extraBufferCapacity = 4)
    val effects: SharedFlow<ServerConfigUiEffect> = _effects.asSharedFlow()

    private var discoveryJob: Job? = null

    fun onUrlChanged(value: String) {
        updateUrlState(value)
    }

    fun clearUrl() {
        updateUrlState("")
    }

    fun applyPreset(url: String) {
        updateUrlState(url)
    }

    fun selectDiscoveredServer(url: String) {
        updateUrlState(url)
        _uiState.update { it.copy(showMdnsDialog = false) }
        testConnection()
    }

    fun dismissMdnsDialog() {
        _uiState.update { it.copy(showMdnsDialog = false) }
    }

    @OptIn(FlowPreview::class)
    fun discoverLocalServers(forceRescan: Boolean = false) {
        if (!forceRescan && _uiState.value.isDiscoveringMdns) {
            _uiState.update { it.copy(showMdnsDialog = true) }
            return
        }
        val discovery = localDiscovery
        if (discovery == null) {
            _uiState.update {
                it.copy(
                    showMdnsDialog = true,
                    isDiscoveringMdns = false,
                    discoveredServers = emptyList(),
                    mdnsDiscoveryCompleted = true
                )
            }
            return
        }
        discoveryJob?.cancel()
        discoveryJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isDiscoveringMdns = true,
                    showMdnsDialog = true,
                    mdnsDiscoveryCompleted = false
                )
            }
            var latestServers: List<DiscoveredLocalServer> = emptyList()
            try {
                discovery.discoverLocalServersFlow()
                    .debounce(MDNS_DEBOUNCE_MS)
                    .collect { rawServers ->
                        val filtered = rawServers.filter { isSafeMdnsEndpoint(it.baseUrl) }
                        latestServers = filtered
                        _uiState.update { current ->
                            current.copy(discoveredServers = filtered)
                        }
                    }
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Throwable) {
                // Keep any resolved servers received before stream failure.
            }
            val firstUrl = latestServers.firstOrNull()?.baseUrl
            _uiState.update { current ->
                val updatedUrl = firstUrl ?: current.urlInput
                if (firstUrl != null) {
                    savedStateHandle[KEY_URL_INPUT] = firstUrl
                }
                current.copy(
                    urlInput = updatedUrl,
                    isDiscoveringMdns = false,
                    discoveredServers = latestServers,
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
                probeIsTls = value.trim().startsWith("https://", ignoreCase = true),
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
                            lastVerifiedEndpoint = if (ok) raw else it.lastVerifiedEndpoint,
                            probeErrorMessage = if (ok) null else UiText.Raw("HTTP ${response.status.value}")
                        )
                    }
                    _effects.emit(
                        ServerConfigUiEffect.ShowProbeResult(
                            success = ok,
                            detail = UiText.Raw("HTTP ${response.status.value} ($elapsedMs ms)")
                        )
                    )
                },
                onFailure = { err ->
                    val detail = err.toUiText(R.string.server_msg_network_error)
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
                lastVerifiedEndpoint = it.lastVerifiedEndpoint ?: normalized,
                showCleartextWarning = isUnlistedCleartextUrl(normalized)
            )
        }
        return true
    }

    private fun isValidHttpUrl(raw: String): Boolean {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return false
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return false
        if (!uri.userInfo.isNullOrEmpty()) return false
        if (!uri.rawQuery.isNullOrEmpty() || !uri.rawFragment.isNullOrEmpty()) return false
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme != "http" && scheme != "https") return false
        val host = uri.host?.trim()?.lowercase().orEmpty()
        if (host.isEmpty() || host.contains(' ') || host == "0.0.0.0") return false
        if (host != "localhost" && !host.contains('.')) return false
        return uri.port == -1 || uri.port in 1..65535
    }

    private fun isSafeMdnsEndpoint(raw: String): Boolean {
        if (!isValidHttpUrl(raw)) return false
        val uri = runCatching { URI(raw.trim()) }.getOrNull() ?: return false
        val host = uri.host?.lowercase() ?: return false
        if (host.endsWith(".local") || host == "localhost" || host == "127.0.0.1" || host == "10.0.2.2") {
            return true
        }
        return isRfc1918Ipv4(host)
    }

    private fun isRfc1918Ipv4(host: String): Boolean {
        val parts = host.split('.')
        if (parts.size != 4) return false
        val octets = parts.map { it.toIntOrNull() ?: return false }
        if (octets.any { it !in 0..255 }) return false
        return when {
            octets[0] == 10 -> true
            octets[0] == 192 && octets[1] == 168 -> true
            octets[0] == 172 && octets[1] in 16..31 -> true
            else -> false
        }
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
        private const val MDNS_DEBOUNCE_MS = 300L
        private val ALLOWED_CLEARTEXT_HOSTS = setOf(
            "10.0.2.2",
            "127.0.0.1",
            "localhost",
            "192.168.1.100",
            "192.168.1.1",
            "192.168.0.100",
            "192.168.10.10",
            "cassy.local",
            "pos-lokal.cassy.local"
        )
    }
}
