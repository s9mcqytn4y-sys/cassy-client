package com.azuredevid.cassy.data.repository

import com.azuredevid.cassy.data.network.HttpErrorMapper
import com.azuredevid.cassy.domain.repository.ServiceRequest
import com.azuredevid.cassy.domain.repository.ServiceRequestRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
private data class ServiceRequestDto(
    val id: String,
    @SerialName("table_number") val tableNumber: String = "",
    val label: String = "",
    val note: String? = null,
    val status: String = "open",
    @SerialName("created_at") val createdAt: String = ""
) {
    fun toDomain() = ServiceRequest(id, tableNumber, label, note, status, createdAt)
}

@Serializable
private data class ServiceRequestListDto(@SerialName("service_requests") val serviceRequests: List<ServiceRequestDto> = emptyList())

/** Ktor client for the staff service request queue. */
class KtorServiceRequestRepository(
    private val httpClient: HttpClient,
    private val serverSettings: ServerSettingsRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ServiceRequestRepository {

    private suspend fun baseUrl() = serverSettings.getBaseUrl().trimEnd('/')

    override suspend fun listActive(): Result<List<ServiceRequest>> = withContext(ioDispatcher) {
        try {
            // The API filters one status per call; the queue needs both waiting states.
            val merged = listOf("open", "acknowledged").flatMap { status ->
                val response = httpClient.get("${baseUrl()}/api/v1/store/service-requests") { parameter("status", status) }
                if (!response.status.isSuccess()) {
                    return@withContext Result.failure(HttpErrorMapper.toDomainException(response, "Gagal memuat panggilan pelayan."))
                }
                response.body<ServiceRequestListDto>().serviceRequests
            }
            Result.success(merged.sortedBy { it.createdAt }.map { it.toDomain() })
        } catch (t: Throwable) {
            Result.failure(HttpErrorMapper.fromThrowable(t, "Gagal memuat panggilan pelayan."))
        }
    }

    override suspend fun acknowledge(id: String): Result<ServiceRequest> = transition(id, "acknowledge")

    override suspend fun markDone(id: String): Result<ServiceRequest> = transition(id, "done")

    override suspend fun cancelAndMute(id: String): Result<ServiceRequest> = transition(id, "cancel")

    private suspend fun transition(id: String, action: String): Result<ServiceRequest> = withContext(ioDispatcher) {
        try {
            val response = httpClient.post("${baseUrl()}/api/v1/store/service-requests/$id/$action")
            if (!response.status.isSuccess()) {
                return@withContext Result.failure(HttpErrorMapper.toDomainException(response, "Gagal memperbarui panggilan pelayan."))
            }
            Result.success(response.body<ServiceRequestDto>().toDomain())
        } catch (t: Throwable) {
            Result.failure(HttpErrorMapper.fromThrowable(t, "Gagal memperbarui panggilan pelayan."))
        }
    }
}
