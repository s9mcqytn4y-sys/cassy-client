package com.azuredevid.cassy.data.repository

import com.azuredevid.cassy.data.model.TableBoardResponseDto
import com.azuredevid.cassy.data.model.TableDto
import com.azuredevid.cassy.data.model.TableInputDto
import com.azuredevid.cassy.data.network.HttpErrorMapper
import com.azuredevid.cassy.domain.model.TableDraft
import com.azuredevid.cassy.domain.model.TableEntry
import com.azuredevid.cassy.domain.repository.TableManagementRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.patch
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
 * Ktor implementation of [TableManagementRepository] on the shared, authenticated [HttpClient]
 * (bearer refresh and CSRF header live in `CassyHttpClientFactory`). All I/O runs on [ioDispatcher].
 * Table ids are path-encoded; fallback messages are only used when the backend sends no `error` text.
 */
class KtorTableManagementRepository(
    private val httpClient: HttpClient,
    private val serverSettings: ServerSettingsRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : TableManagementRepository {

    private fun tablesUrl(suffix: String = ""): String =
        serverSettings.getBaseUrl().trimEnd('/') + "/api/v1/pos/tables" + suffix

    private fun tableUrl(id: String, action: String = ""): String =
        tablesUrl("/" + id.encodeURLPathPart() + action)

    override suspend fun listTables(): Result<List<TableEntry>> =
        call(
            fallbackMessage = "Gagal memuat daftar meja.",
            request = { httpClient.get(tablesUrl()) },
            decode = { it.body<TableBoardResponseDto>().tables.map(TableDto::toDomain) }
        )

    override suspend fun createTable(draft: TableDraft): Result<TableEntry> =
        call(
            fallbackMessage = "Gagal menambah meja.",
            request = {
                httpClient.post(tablesUrl()) {
                    contentType(ContentType.Application.Json)
                    setBody(TableInputDto.from(draft))
                }
            },
            decode = { it.body<TableDto>().toDomain() }
        )

    override suspend fun updateTable(id: String, draft: TableDraft): Result<TableEntry> =
        call(
            fallbackMessage = "Gagal menyimpan meja.",
            request = {
                httpClient.patch(tableUrl(id)) {
                    contentType(ContentType.Application.Json)
                    setBody(TableInputDto.from(draft))
                }
            },
            decode = { it.body<TableDto>().toDomain() }
        )

    override suspend fun archiveTable(id: String): Result<Unit> =
        call(
            fallbackMessage = "Gagal mengarsipkan meja.",
            request = { httpClient.post(tableUrl(id, "/archive")) },
            decode = { }
        )

    override suspend fun rotateQr(id: String): Result<TableEntry> =
        call(
            fallbackMessage = "Gagal mengganti QR meja.",
            request = { httpClient.post(tableUrl(id, "/rotate-qr")) },
            decode = { it.body<TableDto>().toDomain() }
        )

    /**
     * Runs [request] and [decode] on [ioDispatcher], maps non-2xx responses through [HttpErrorMapper],
     * and turns transport or decoding failures into a `DomainException`.
     */
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
