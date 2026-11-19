package com.azuredevid.cassy.data.repository

import com.azuredevid.cassy.data.network.HttpErrorMapper
import com.azuredevid.cassy.data.network.deviceAuth
import com.azuredevid.cassy.domain.repository.DeviceProfile
import com.azuredevid.cassy.domain.repository.DeviceProfileRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
private data class DeviceCatalogDto(
    @SerialName("device_role") val role: String = "pos",
    val features: Map<String, Boolean> = emptyMap()
)

/** Reads the device role and store feature flags from `GET /api/v1/device/catalog`. */
class KtorDeviceProfileRepository(
    private val httpClient: HttpClient,
    private val serverSettings: ServerSettingsRepository,
    private val deviceCredential: () -> String?,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : DeviceProfileRepository {

    // shortcut: the response also carries the full catalog, which is ignored here; add a slim endpoint if the payload slows start-up.
    override suspend fun profile(): Result<DeviceProfile> = withContext(ioDispatcher) {
        val credential = deviceCredential() ?: return@withContext Result.failure(IllegalStateException("Perangkat belum terdaftar."))
        try {
            val response = httpClient.get("${serverSettings.getBaseUrl().trimEnd('/')}/api/v1/device/catalog") { deviceAuth(credential) }
            if (!response.status.isSuccess()) {
                return@withContext Result.failure(HttpErrorMapper.toDomainException(response, "Gagal membaca peran perangkat."))
            }
            val dto = response.body<DeviceCatalogDto>()
            Result.success(DeviceProfile(dto.role, dto.features))
        } catch (t: Throwable) {
            Result.failure(HttpErrorMapper.fromThrowable(t, "Gagal membaca peran perangkat."))
        }
    }
}
