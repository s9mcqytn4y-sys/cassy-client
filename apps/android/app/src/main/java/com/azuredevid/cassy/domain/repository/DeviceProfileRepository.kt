package com.azuredevid.cassy.domain.repository

/** What the owner set for this tablet: its role (`pos`, `kds`, `backoffice`, `queue_display`) and store flags. */
data class DeviceProfile(val role: String, val features: Map<String, Boolean> = emptyMap())

interface DeviceProfileRepository {
    suspend fun profile(): Result<DeviceProfile>
}
