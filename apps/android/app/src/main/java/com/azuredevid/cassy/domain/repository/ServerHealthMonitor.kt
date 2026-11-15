package com.azuredevid.cassy.domain.repository

import kotlinx.coroutines.flow.Flow

enum class ServerConnectionStatus {
    CHECKING,
    ONLINE,
    NETWORK_OFFLINE,
    SERVER_UNREACHABLE
}

data class ServerConnectivityState(
    val status: ServerConnectionStatus = ServerConnectionStatus.CHECKING,
    val latencyMs: Long? = null
)

/**
 * Observes OS network availability and live backend /api/v1/health reachability.
 */
interface ServerHealthMonitor {
    fun observeConnectivity(): Flow<ServerConnectivityState>
    fun requestProbe()
    suspend fun probeNow(): ServerConnectivityState
}
