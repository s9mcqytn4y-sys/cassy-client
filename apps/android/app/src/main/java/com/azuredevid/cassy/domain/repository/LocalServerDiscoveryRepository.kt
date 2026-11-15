package com.azuredevid.cassy.domain.repository

import androidx.compose.runtime.Immutable

@Immutable
data class DiscoveredLocalServer(
    val serviceName: String,
    val host: String,
    val port: Int,
    val baseUrl: String,
    val isTls: Boolean = false
)

interface LocalServerDiscoveryRepository {
    suspend fun discoverLocalServers(timeoutMs: Long = DEFAULT_DISCOVERY_TIMEOUT_MS): List<DiscoveredLocalServer>

    companion object {
        const val DEFAULT_DISCOVERY_TIMEOUT_MS = 3_500L
    }
}
