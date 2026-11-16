package com.azuredevid.cassy.data.repository

import com.azuredevid.cassy.data.model.KdsTicketDto
import com.azuredevid.cassy.data.model.KdsTicketsResponseDto
import com.azuredevid.cassy.data.model.KdsTransitionRequestDto
import com.azuredevid.cassy.data.network.HttpErrorMapper
import com.azuredevid.cassy.domain.error.DomainError
import com.azuredevid.cassy.domain.error.DomainException
import com.azuredevid.cassy.domain.model.KdsStation
import com.azuredevid.cassy.domain.model.KdsTicket
import com.azuredevid.cassy.domain.model.KdsTicketStatus
import com.azuredevid.cassy.domain.repository.KdsRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Ktor implementation of [KdsRepository] on the shared authenticated client. Tickets of an unknown
 * station are dropped. All I/O runs on [ioDispatcher].
 */
class KtorKdsRepository(
    private val httpClient: HttpClient,
    private val serverSettings: ServerSettingsRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : KdsRepository {

    private fun url(suffix: String): String = serverSettings.getBaseUrl().trimEnd('/') + "/api/v1/kds/tickets" + suffix

    override suspend fun listTickets(station: KdsStation): Result<List<KdsTicket>> =
        call(
            fallbackMessage = "Gagal memuat tiket dapur.",
            request = { httpClient.get(url("")) { parameter("station", station.wireValue) } },
            decode = { response -> response.body<KdsTicketsResponseDto>().tickets.mapNotNull(KdsTicketDto::toDomain) }
        )

    override suspend fun transition(ticketId: String, to: KdsTicketStatus, expected: KdsTicketStatus): Result<KdsTicket> =
        call(
            fallbackMessage = "Gagal memperbarui tiket.",
            request = {
                httpClient.post(url("/" + ticketId.encodeURLPathPart() + "/transition")) {
                    contentType(ContentType.Application.Json)
                    setBody(KdsTransitionRequestDto(to = to.wireValue, expectedStatus = expected.wireValue))
                }
            },
            decode = { response ->
                response.body<KdsTicketDto>().toDomain()
                    ?: throw DomainException(DomainError.ServerError("Stasiun tiket tidak dikenali."))
            }
        )

    private suspend fun <T> call(
        fallbackMessage: String,
        request: suspend () -> HttpResponse,
        decode: suspend (HttpResponse) -> T
    ): Result<T> = withContext(ioDispatcher) {
        try {
            val response = request()
            if (response.status.isSuccess()) {
                Result.success(decode(response))
            } else {
                Result.failure(HttpErrorMapper.toDomainException(response, fallbackMessage))
            }
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            Result.failure(HttpErrorMapper.fromThrowable(t, fallbackMessage))
        }
    }
}
