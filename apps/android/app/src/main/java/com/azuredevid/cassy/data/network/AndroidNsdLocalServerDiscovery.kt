package com.azuredevid.cassy.data.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import com.azuredevid.cassy.domain.repository.DiscoveredLocalServer
import com.azuredevid.cassy.domain.repository.LocalServerDiscoveryRepository
import java.net.Inet4Address
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.suspendCancellableCoroutine

class AndroidNsdLocalServerDiscovery(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : LocalServerDiscoveryRepository {

    private val appContext = context.applicationContext

    override suspend fun discoverLocalServers(timeoutMs: Long): List<DiscoveredLocalServer> =
        withContext(ioDispatcher) {
            val nsdManager = appContext.getSystemService(Context.NSD_SERVICE) as? NsdManager
                ?: return@withContext emptyList()
            val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val multicastLock = wifiManager?.createMulticastLock("cassy_mdns_lock")?.apply {
                setReferenceCounted(false)
            }

            val found = ConcurrentHashMap<String, DiscoveredLocalServer>()
            val resolveMutex = Mutex()
            val activeListeners = mutableListOf<NsdManager.DiscoveryListener>()

            try {
                runCatching { multicastLock?.acquire() }

                supervisorScope {
                    for (serviceType in SERVICE_TYPES) {
                        val listener = object : NsdManager.DiscoveryListener {
                            override fun onDiscoveryStarted(regType: String) = Unit

                            override fun onServiceFound(service: NsdServiceInfo) {
                                launch {
                                    resolveMutex.withLock {
                                        val resolved = withTimeoutOrNull(RESOLVE_TIMEOUT_MS) {
                                            resolveServiceSuspend(nsdManager, service)
                                        }
                                        if (resolved != null) {
                                            val candidate = toDiscoveredServer(resolved)
                                            if (candidate != null) {
                                                found[candidate.baseUrl] = candidate
                                            }
                                        }
                                    }
                                }
                            }

                            override fun onServiceLost(service: NsdServiceInfo) = Unit
                            override fun onDiscoveryStopped(serviceType: String) = Unit

                            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                                runCatching { nsdManager.stopServiceDiscovery(this) }
                            }

                            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                                runCatching { nsdManager.stopServiceDiscovery(this) }
                            }
                        }

                        val started = runCatching {
                            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
                            true
                        }.getOrDefault(false)

                        if (started) {
                            activeListeners.add(listener)
                        }
                    }

                    delay(timeoutMs.coerceIn(500L, 10_000L))
                }
            } finally {
                for (listener in activeListeners) {
                    runCatching { nsdManager.stopServiceDiscovery(listener) }
                }
                runCatching {
                    if (multicastLock?.isHeld == true) {
                        multicastLock.release()
                    }
                }
            }

            found.values.sortedBy { it.serviceName }
        }

    @Suppress("DEPRECATION")
    private suspend fun resolveServiceSuspend(
        nsdManager: NsdManager,
        serviceInfo: NsdServiceInfo
    ): NsdServiceInfo? = suspendCancellableCoroutine { cont ->
        val listener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                if (cont.isActive) cont.resume(null)
            }

            override fun onServiceResolved(resolvedInfo: NsdServiceInfo) {
                if (cont.isActive) cont.resume(resolvedInfo)
            }
        }
        runCatching {
            nsdManager.resolveService(serviceInfo, listener)
        }.onFailure {
            if (cont.isActive) cont.resume(null)
        }
    }

    @Suppress("DEPRECATION")
    private fun toDiscoveredServer(info: NsdServiceInfo): DiscoveredLocalServer? {
        val port = info.port
        if (port <= 0) return null
        val inetAddress = info.host ?: return null
        val hostAddress = inetAddress.hostAddress ?: return null
        if (inetAddress !is Inet4Address && hostAddress.contains(':')) {
            return null
        }
        val rawName = info.serviceName?.trim().orEmpty().ifBlank { "Cassy Local Server" }
        val lowerName = rawName.lowercase()
        val isTls = port == 443 || port == 8443 || lowerName.contains("https") || lowerName.contains("tls")
        val scheme = if (isTls) "https" else "http"
        val defaultPort = (isTls && port == 443) || (!isTls && port == 80)
        val baseUrl = if (defaultPort) "$scheme://$hostAddress" else "$scheme://$hostAddress:$port"
        return DiscoveredLocalServer(
            serviceName = rawName,
            host = hostAddress,
            port = port,
            baseUrl = baseUrl,
            isTls = isTls
        )
    }

    companion object {
        private val SERVICE_TYPES = listOf("_cassy._tcp.", "_http._tcp.")
        private const val RESOLVE_TIMEOUT_MS = 1_200L
    }
}
