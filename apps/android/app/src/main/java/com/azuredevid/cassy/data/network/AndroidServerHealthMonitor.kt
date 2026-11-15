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

/**
 * Lifecycle-aware health monitor combining Android ConnectivityManager callbacks
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
            observeNetworkAvailability(),
            manualTriggers.onStart { emit(Unit) }
        ) { hasNetwork, _ -> hasNetwork }
            .transformLatest { hasNetwork ->
                if (!hasNetwork) {
                    emit(ServerConnectivityState(status = ServerConnectionStatus.NETWORK_OFFLINE))
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
        if (!hasActiveNetwork()) {
            return@withContext ServerConnectivityState(status = ServerConnectionStatus.NETWORK_OFFLINE)
        }
        val baseUrl = serverSettings.getBaseUrl().trimEnd('/')
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
            ServerConnectivityState(status = ServerConnectionStatus.SERVER_UNREACHABLE)
        }
    }

    private fun observeNetworkAvailability(): Flow<Boolean> = callbackFlow {
        val cm = appContext?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (cm == null) {
            trySend(true)
            awaitClose {}
            return@callbackFlow
        }

        trySend(hasActiveNetwork(cm))

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                trySend(hasInternet)
            }

            override fun onLost(network: Network) {
                trySend(hasActiveNetwork(cm))
            }

            override fun onUnavailable() {
                trySend(false)
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

    private fun hasActiveNetwork(
        cm: ConnectivityManager? = appContext?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    ): Boolean {
        if (cm == null) return true
        return runCatching {
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }.getOrDefault(true)
    }

    companion object {
        private const val PROBE_CONNECT_TIMEOUT_MS = 3_000L
        private const val PROBE_REQUEST_TIMEOUT_MS = 4_000L
        private const val DEFAULT_POLL_INTERVAL_MS = 30_000L
    }
}
