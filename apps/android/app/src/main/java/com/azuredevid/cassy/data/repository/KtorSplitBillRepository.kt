package com.azuredevid.cassy.data.repository

import com.azuredevid.cassy.data.model.DivideOrderRequestDto
import com.azuredevid.cassy.data.model.DivideOrderResponseDto
import com.azuredevid.cassy.data.model.OrderListResponseDto
import com.azuredevid.cassy.data.model.OrderWithSplitsDto
import com.azuredevid.cassy.data.network.HttpErrorMapper
import com.azuredevid.cassy.domain.model.OpenSplitBillSummary
import com.azuredevid.cassy.domain.model.SplitBill
import com.azuredevid.cassy.domain.model.SplitPlan
import com.azuredevid.cassy.domain.repository.SplitBillRepository
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

/** Ktor implementation of [SplitBillRepository] on the shared authenticated client. All I/O runs on [ioDispatcher]. */
class KtorSplitBillRepository(
    private val httpClient: HttpClient,
    private val serverSettings: ServerSettingsRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : SplitBillRepository {

    private fun api(path: String) = serverSettings.getBaseUrl().trimEnd('/') + "/api/v1/pos" + path

    override suspend fun divide(orderId: String, plan: SplitPlan): Result<SplitBill> = call(
        fallbackMessage = "Gagal membagi tagihan.",
        request = {
            httpClient.post(api("/orders/" + orderId.encodeURLPathPart() + "/splits")) {
                contentType(ContentType.Application.Json)
                setBody(DivideOrderRequestDto.from(plan))
            }
        },
        decode = { it.body<DivideOrderResponseDto>().toDomain() }
    )

    override suspend fun getBill(orderId: String): Result<SplitBill> = call(
        fallbackMessage = "Gagal memuat tagihan.",
        request = { httpClient.get(api("/orders/" + orderId.encodeURLPathPart())) },
        decode = { it.body<OrderWithSplitsDto>().toDomain() }
    )

    override suspend fun listOpenBills(query: String): Result<List<OpenSplitBillSummary>> = call(
        fallbackMessage = "Gagal memuat tagihan terbuka.",
        request = {
            httpClient.get(api("/orders")) {
                parameter("payment_status", "partially_paid")
                if (query.isNotBlank()) parameter("q", query.trim())
            }
        },
        decode = { it.body<OrderListResponseDto>().items.map { item -> item.toDomain() } }
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
