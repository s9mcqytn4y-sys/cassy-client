package com.azuredevid.cassy.data.repository

import com.azuredevid.cassy.data.network.HttpErrorMapper
import com.azuredevid.cassy.domain.repository.StorePrinter
import com.azuredevid.cassy.domain.repository.StorePrinterInput
import com.azuredevid.cassy.domain.repository.StorePrinterRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
private data class PrinterDto(
    val id: String,
    val name: String = "",
    val station: String = "",
    @SerialName("lan_address") val lanAddress: String? = null,
    @SerialName("paper_width_mm") val paperWidthMm: Int = 58,
    @SerialName("is_active") val isActive: Boolean = true
) {
    fun toDomain() = StorePrinter(id, name, station, lanAddress, paperWidthMm, isActive)
}

@Serializable
private data class PrinterListDto(val printers: List<PrinterDto> = emptyList())

@Serializable
private data class PrinterInputDto(
    val name: String,
    val station: String,
    @SerialName("lan_address") val lanAddress: String,
    @SerialName("paper_width_mm") val paperWidthMm: Int
)

/** Ktor client for `/api/v1/pos/printers` (any staff) and `/api/v1/store/printers` (owner). */
class KtorStorePrinterRepository(
    private val httpClient: HttpClient,
    private val serverSettings: ServerSettingsRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : StorePrinterRepository {

    private suspend fun baseUrl() = serverSettings.getBaseUrl().trimEnd('/')

    override suspend fun listForTablet(): Result<List<StorePrinter>> = list("/api/v1/pos/printers")

    override suspend fun listAll(): Result<List<StorePrinter>> = list("/api/v1/store/printers")

    override suspend fun create(input: StorePrinterInput): Result<StorePrinter> =
        save { httpClient.post("${baseUrl()}/api/v1/store/printers") { json(input) } }

    override suspend fun update(id: String, input: StorePrinterInput): Result<StorePrinter> =
        save { httpClient.patch("${baseUrl()}/api/v1/store/printers/$id") { json(input) } }

    private fun HttpRequestBuilder.json(input: StorePrinterInput) {
        contentType(ContentType.Application.Json)
        setBody(PrinterInputDto(input.name.trim(), input.station, input.lanAddress.trim(), input.paperWidthMm))
    }

    private suspend fun list(path: String): Result<List<StorePrinter>> = withContext(ioDispatcher) {
        try {
            val response = httpClient.get("${baseUrl()}$path")
            if (!response.status.isSuccess()) {
                return@withContext Result.failure(HttpErrorMapper.toDomainException(response, "Gagal memuat printer."))
            }
            Result.success(response.body<PrinterListDto>().printers.map { it.toDomain() })
        } catch (t: Throwable) {
            Result.failure(HttpErrorMapper.fromThrowable(t, "Gagal memuat printer."))
        }
    }

    private suspend fun save(call: suspend () -> HttpResponse): Result<StorePrinter> = withContext(ioDispatcher) {
        try {
            val response = call()
            if (!response.status.isSuccess()) {
                return@withContext Result.failure(HttpErrorMapper.toDomainException(response, "Gagal menyimpan printer."))
            }
            Result.success(response.body<PrinterDto>().toDomain())
        } catch (t: Throwable) {
            Result.failure(HttpErrorMapper.fromThrowable(t, "Gagal menyimpan printer."))
        }
    }
}
