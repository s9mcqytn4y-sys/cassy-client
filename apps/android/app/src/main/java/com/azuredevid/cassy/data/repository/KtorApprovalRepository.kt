package com.azuredevid.cassy.data.repository

import com.azuredevid.cassy.data.network.HttpErrorMapper
import com.azuredevid.cassy.domain.repository.ApprovalRepository
import com.azuredevid.cassy.domain.repository.OwnerApproval
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
private data class ApprovalParamsDto(val quantity: Int)

@Serializable
private data class CreateApprovalRequestDto(
    val action: String,
    @SerialName("reference_id") val referenceId: String,
    val params: ApprovalParamsDto,
    val reason: String
)

@Serializable
private data class ApprovalDto(
    val id: String,
    val status: String,
    @SerialName("decision_note") val decisionNote: String = ""
)

/** Ktor client for `/api/v1/pos/approvals`. */
class KtorApprovalRepository(
    private val httpClient: HttpClient,
    private val serverSettings: ServerSettingsRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ApprovalRepository {

    private suspend fun baseUrl() = serverSettings.getBaseUrl().trimEnd('/')

    override suspend fun requestVoidItem(itemId: String, quantity: Int, reason: String): Result<OwnerApproval> =
        withContext(ioDispatcher) {
            try {
                val response = httpClient.post("${baseUrl()}/api/v1/pos/approvals") {
                    contentType(ContentType.Application.Json)
                    setBody(CreateApprovalRequestDto("void_item", itemId, ApprovalParamsDto(quantity), reason))
                }
                if (!response.status.isSuccess()) {
                    return@withContext Result.failure(HttpErrorMapper.toDomainException(response, "Gagal mengirim permintaan ke owner."))
                }
                Result.success(response.body<ApprovalDto>().toDomain())
            } catch (t: Throwable) {
                Result.failure(HttpErrorMapper.fromThrowable(t, "Gagal mengirim permintaan ke owner."))
            }
        }

    override suspend fun get(id: String): Result<OwnerApproval> = withContext(ioDispatcher) {
        try {
            val response = httpClient.get("${baseUrl()}/api/v1/pos/approvals/$id")
            if (!response.status.isSuccess()) {
                return@withContext Result.failure(HttpErrorMapper.toDomainException(response, "Gagal memuat status persetujuan."))
            }
            Result.success(response.body<ApprovalDto>().toDomain())
        } catch (t: Throwable) {
            Result.failure(HttpErrorMapper.fromThrowable(t, "Gagal memuat status persetujuan."))
        }
    }

    private fun ApprovalDto.toDomain() = OwnerApproval(id, status, decisionNote)
}
