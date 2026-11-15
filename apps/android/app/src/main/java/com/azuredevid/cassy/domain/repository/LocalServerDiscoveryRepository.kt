package com.azuredevid.cassy.domain.repository

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

@Immutable
data class DiscoveredLocalServer(
    val serviceName: String,
    val host: String,
    val port: Int,
    val baseUrl: String,
    val isTls: Boolean = false,
    val latencyMs: Long? = null
)

interface LocalServerDiscoveryRepository {
    suspend fun discoverLocalServers(timeoutMs: Long = DEFAULT_DISCOVERY_TIMEOUT_MS): List<DiscoveredLocalServer>

    fun discoverLocalServersFlow(timeoutMs: Long = DEFAULT_DISCOVERY_TIMEOUT_MS): Flow<List<DiscoveredLocalServer>> =
        flow { emit(discoverLocalServers(timeoutMs)) }

    companion object {
        const val DEFAULT_DISCOVERY_TIMEOUT_MS = 3_500L
    }
}
