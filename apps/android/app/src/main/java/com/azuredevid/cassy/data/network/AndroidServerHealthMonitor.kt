package com.azuredevid.cassy.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.azuredevid.cassy.data.repository.ServerSettingsRepository
import com.azuredevid.cassy.domain.repository.ServerConnectionStatus
import com.azuredevid.cassy.domain.repository.ServerConnectivityState
import com.azuredevid.cassy.domain.repository.ServerHealthMonitor
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.time.TimeSource

internal data class OsNetworkSnapshot(
    val hasTransport: Boolean = true,
    val isValidatedInternet: Boolean = true,
    val isCaptivePortal: Boolean = false
)

/**
 * Lifecycle-aware health monitor combining Android ConnectivityManager capabilities
 * (distinguishing No Network vs Captive Portal / Unvalidated WAN vs Server Unreachable)
 * with periodic /api/v1/health probes only while collectors are active.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AndroidServerHealthMonitor(
    context: Context? = null,
    private val httpClient: HttpClient,
    private val serverSettings: ServerSettingsRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val pollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS
) : ServerHealthMonitor {

    private val appContext = context?.applicationContext
    private val manualTriggers = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override fun requestProbe() {
        manualTriggers.tryEmit(Unit)
    }

    override fun observeConnectivity(): Flow<ServerConnectivityState> =
        combine(
            observeNetworkSnapshot(),
            serverSettings.baseUrlFlow,
            manualTriggers.onStart { emit(Unit) }
        ) { snapshot, baseUrl, _ -> snapshot to baseUrl }
            .transformLatest { (snapshot, baseUrl) ->
                if (!snapshot.hasTransport) {
                    emit(ServerConnectivityState(status = ServerConnectionStatus.NETWORK_OFFLINE))
                    return@transformLatest
                }
                val isLocalEndpoint = isLocalOrPrivateEndpoint(baseUrl)
                if (!isLocalEndpoint && (snapshot.isCaptivePortal || !snapshot.isValidatedInternet)) {
                    emit(ServerConnectivityState(status = ServerConnectionStatus.INTERNET_UNVALIDATED))
                    return@transformLatest
                }
                emit(ServerConnectivityState(status = ServerConnectionStatus.CHECKING))
                while (currentCoroutineContext().isActive) {
                    emit(probeNow())
                    delay(pollIntervalMs)
                }
            }
            .distinctUntilChanged()

    override suspend fun probeNow(): ServerConnectivityState = withContext(ioDispatcher) {
        val snapshot = readCurrentNetworkSnapshot()
        if (!snapshot.hasTransport) {
            return@withContext ServerConnectivityState(status = ServerConnectionStatus.NETWORK_OFFLINE)
        }
        val baseUrl = serverSettings.getBaseUrl().trimEnd('/')
        val isLocalEndpoint = isLocalOrPrivateEndpoint(baseUrl)
        if (!isLocalEndpoint && (snapshot.isCaptivePortal || !snapshot.isValidatedInternet)) {
            return@withContext ServerConnectivityState(status = ServerConnectionStatus.INTERNET_UNVALIDATED)
        }
        val mark = TimeSource.Monotonic.markNow()
        try {
            val response = httpClient.get("$baseUrl/api/v1/health") {
                timeout {
                    connectTimeoutMillis = PROBE_CONNECT_TIMEOUT_MS
                    requestTimeoutMillis = PROBE_REQUEST_TIMEOUT_MS
                }
            }
            val elapsedMs = mark.elapsedNow().inWholeMilliseconds.coerceAtLeast(1L)
            if (response.status.isSuccess()) {
                ServerConnectivityState(
                    status = ServerConnectionStatus.ONLINE,
                    latencyMs = elapsedMs
                )
            } else {
                ServerConnectivityState(status = ServerConnectionStatus.SERVER_UNREACHABLE)
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Throwable) {
            val latestSnapshot = readCurrentNetworkSnapshot()
            if (!latestSnapshot.hasTransport) {
                ServerConnectivityState(status = ServerConnectionStatus.NETWORK_OFFLINE)
            } else if (!isLocalEndpoint && (latestSnapshot.isCaptivePortal || !latestSnapshot.isValidatedInternet)) {
                ServerConnectivityState(status = ServerConnectionStatus.INTERNET_UNVALIDATED)
            } else {
                ServerConnectivityState(status = ServerConnectionStatus.SERVER_UNREACHABLE)
            }
        }
    }

    private fun observeNetworkSnapshot(): Flow<OsNetworkSnapshot> = callbackFlow {
        val cm = appContext?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (cm == null) {
            trySend(OsNetworkSnapshot())
            awaitClose {}
            return@callbackFlow
        }

        trySend(readCurrentNetworkSnapshot(cm))

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(readCurrentNetworkSnapshot(cm))
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(snapshotFromCapabilities(capabilities))
            }

            override fun onLost(network: Network) {
                trySend(readCurrentNetworkSnapshot(cm))
            }

            override fun onUnavailable() {
                trySend(OsNetworkSnapshot(hasTransport = false, isValidatedInternet = false))
            }
        }

        val registered = runCatching {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            cm.registerNetworkCallback(request, callback)
            true
        }.getOrDefault(false)

        awaitClose {
            if (registered) {
                runCatching { cm.unregisterNetworkCallback(callback) }
            }
        }
    }.distinctUntilChanged()

    private fun readCurrentNetworkSnapshot(
        cm: ConnectivityManager? = appContext?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    ): OsNetworkSnapshot {
        if (cm == null) return OsNetworkSnapshot()
        return runCatching {
            val network = cm.activeNetwork
                ?: return OsNetworkSnapshot(hasTransport = false, isValidatedInternet = false)
            val caps = cm.getNetworkCapabilities(network)
                ?: return OsNetworkSnapshot(hasTransport = false, isValidatedInternet = false)
            snapshotFromCapabilities(caps)
        }.getOrDefault(OsNetworkSnapshot())
    }

    private fun snapshotFromCapabilities(caps: NetworkCapabilities): OsNetworkSnapshot {
        val hasInternetCap = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val isValidated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val isCaptive = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)
        return OsNetworkSnapshot(
            hasTransport = hasInternetCap,
            isValidatedInternet = isValidated,
            isCaptivePortal = isCaptive
        )
    }

    companion object {
        private const val PROBE_CONNECT_TIMEOUT_MS = 3_000L
        private const val PROBE_REQUEST_TIMEOUT_MS = 4_000L
        private const val DEFAULT_POLL_INTERVAL_MS = 30_000L

        internal fun isLocalOrPrivateEndpoint(baseUrl: String): Boolean {
            val host = runCatching { URI(baseUrl.trim()).host?.lowercase() }.getOrNull() ?: return false
            if (host == "localhost" || host == "127.0.0.1" || host == "10.0.2.2" || host.endsWith(".local")) {
                return true
            }
            if (host.startsWith("192.168.") || host.startsWith("10.")) {
                return true
            }
            if (host.startsWith("172.")) {
                val secondOctet = host.removePrefix("172.").substringBefore('.').toIntOrNull()
                if (secondOctet != null && secondOctet in 16..31) {
                    return true
                }
            }
            return false
        }
    }
}
