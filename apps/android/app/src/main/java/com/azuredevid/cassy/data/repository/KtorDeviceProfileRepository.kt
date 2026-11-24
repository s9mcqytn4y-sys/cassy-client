package com.azuredevid.cassy.data.repository

import com.azuredevid.cassy.data.local.OfflinePricingSnapshot
import com.azuredevid.cassy.data.local.OfflinePricingStore
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
    @SerialName("device_prefix") val prefix: String = "",
    val features: Map<String, Boolean> = emptyMap(),
    @SerialName("catalog_version") val catalogVersion: String = "",
    @SerialName("cash_rounding_step") val cashRoundingStep: Long = 0,
    val catalog: DeviceCatalogBodyDto? = null
)

@Serializable
private data class DeviceCatalogBodyDto(
    val store: DeviceCatalogStoreDto? = null,
    val products: List<DeviceCatalogProductDto> = emptyList()
)

@Serializable
private data class DeviceCatalogStoreDto(
    val slug: String = "",
    @SerialName("tax_bps") val taxBps: Long = 0,
    @SerialName("service_bps") val serviceBps: Long = 0
)

@Serializable
private data class DeviceCatalogProductDto(
    val id: String,
    val available: Boolean = true,
    val variants: List<DeviceCatalogVariantDto> = emptyList()
)

@Serializable
private data class DeviceCatalogVariantDto(
    val price: Long,
    @SerialName("is_default") val isDefault: Boolean = false,
    val available: Boolean = true
)

/** Default-variant prices the server would use for an order line without a variant (`resolveVariant`). */
private fun DeviceCatalogDto.toOfflinePricing(nowMs: Long): OfflinePricingSnapshot? {
    val body = catalog ?: return null
    if (catalogVersion.isBlank()) return null
    val prices = body.products.filter { it.available }.mapNotNull { product ->
        product.variants.firstOrNull { it.isDefault && it.available }?.let { product.id to it.price }
    }.toMap()
    return OfflinePricingSnapshot(
        storeSlug = body.store?.slug.orEmpty(),
        catalogVersion = catalogVersion,
        devicePrefix = prefix,
        taxBps = body.store?.taxBps ?: 0,
        serviceBps = body.store?.serviceBps ?: 0,
        cashRoundingStep = cashRoundingStep,
        prices = prices,
        fetchedAtMs = nowMs
    )
}

/** Reads the device role and store feature flags from `GET /api/v1/device/catalog`. */
class KtorDeviceProfileRepository(
    private val httpClient: HttpClient,
    private val serverSettings: ServerSettingsRepository,
    private val deviceCredential: () -> String?,
    private val offlinePricing: OfflinePricingStore? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val nowMs: () -> Long = System::currentTimeMillis
) : DeviceProfileRepository {

    // The same response carries the catalog snapshot an offline sale is priced from (S4-Q1).
    override suspend fun profile(): Result<DeviceProfile> = withContext(ioDispatcher) {
        val credential = deviceCredential() ?: return@withContext Result.failure(IllegalStateException("Perangkat belum terdaftar."))
        try {
            val response = httpClient.get("${serverSettings.getBaseUrl().trimEnd('/')}/api/v1/device/catalog") { deviceAuth(credential) }
            if (!response.status.isSuccess()) {
                return@withContext Result.failure(HttpErrorMapper.toDomainException(response, "Gagal membaca peran perangkat."))
            }
            val dto = response.body<DeviceCatalogDto>()
            dto.toOfflinePricing(nowMs())?.let { offlinePricing?.save(it) }
            Result.success(DeviceProfile(dto.role, dto.features))
        } catch (t: Throwable) {
            Result.failure(HttpErrorMapper.fromThrowable(t, "Gagal membaca peran perangkat."))
        }
    }
}
