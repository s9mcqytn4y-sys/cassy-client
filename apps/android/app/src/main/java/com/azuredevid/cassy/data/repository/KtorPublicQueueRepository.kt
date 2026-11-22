package com.azuredevid.cassy.data.repository

import com.azuredevid.cassy.data.network.HttpErrorMapper
import com.azuredevid.cassy.domain.repository.PublicQueueEntry
import com.azuredevid.cassy.domain.repository.PublicQueueRepository
import com.azuredevid.cassy.domain.repository.PublicQueueSnapshot
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
private data class PublicQueueEntryDto(
    @SerialName("queue_number") val queueNumber: String = "",
    val status: String = ""
)

@Serializable
private data class PublicQueueResponseDto(
    @SerialName("store_name") val storeName: String = "",
    @SerialName("store_timezone") val storeTimezone: String = "Asia/Jakarta",
    val orders: List<PublicQueueEntryDto> = emptyList()
)

@Serializable
private data class QueueWsTicketResponseDto(
    val ticket: String = ""
)

/** Ktor client for the public Android TV queue board (`Authorization: Display <token>`). */
class KtorPublicQueueRepository(
    private val httpClient: HttpClient,
    private val serverSettings: ServerSettingsRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : PublicQueueRepository {

    private suspend fun baseUrl() = serverSettings.getBaseUrl().trimEnd('/')

    override suspend fun fetchQueue(displayToken: String): Result<PublicQueueSnapshot> = withContext(ioDispatcher) {
        try {
            val response = httpClient.get("${baseUrl()}/api/v1/public/queue") {
                header(HttpHeaders.Authorization, "Display ${displayToken.trim()}")
            }
            if (!response.status.isSuccess()) {
                return@withContext Result.failure(HttpErrorMapper.toDomainException(response, "Gagal memuat antrean TV."))
            }
            val dto = response.body<PublicQueueResponseDto>()
            Result.success(
                PublicQueueSnapshot(
                    storeName = dto.storeName,
                    storeTimezone = dto.storeTimezone,
                    orders = dto.orders.map { PublicQueueEntry(it.queueNumber, it.status) }
                )
            )
        } catch (t: Throwable) {
            Result.failure(HttpErrorMapper.fromThrowable(t, "Gagal memuat antrean TV."))
        }
    }

    override suspend fun requestWsTicket(displayToken: String): Result<String> = withContext(ioDispatcher) {
        try {
            val response = httpClient.post("${baseUrl()}/api/v1/public/queue/ws-ticket") {
                header(HttpHeaders.Authorization, "Display ${displayToken.trim()}")
            }
            if (!response.status.isSuccess()) {
                return@withContext Result.failure(HttpErrorMapper.toDomainException(response, "Gagal membuat tiket realtime antrean."))
            }
            val ticket = response.body<QueueWsTicketResponseDto>().ticket
            if (ticket.isBlank()) {
                return@withContext Result.failure(IllegalStateException("Tiket WebSocket antrean kosong."))
            }
            Result.success(ticket)
        } catch (t: Throwable) {
            Result.failure(HttpErrorMapper.fromThrowable(t, "Gagal membuat tiket realtime antrean."))
        }
    }
}
