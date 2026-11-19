package com.azuredevid.cassy.data.repository

import com.azuredevid.cassy.data.network.HttpErrorMapper
import com.azuredevid.cassy.domain.repository.CreatedDisplayScreen
import com.azuredevid.cassy.domain.repository.DisplayScreen
import com.azuredevid.cassy.domain.repository.DisplayTokenRepository
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
private data class DisplayTokenDto(
    val id: String,
    val name: String = "",
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("revoked_at") val revokedAt: String? = null,
    @SerialName("last_seen_at") val lastSeenAt: String? = null,
    val token: String? = null,
    @SerialName("display_url") val displayUrl: String? = null
) {
    fun toScreen() = DisplayScreen(id, name, createdAt, revokedAt, lastSeenAt)
}

@Serializable
private data class DisplayTokenListDto(@SerialName("display_tokens") val displayTokens: List<DisplayTokenDto> = emptyList())

@Serializable
private data class CreateDisplayRequestDto(val name: String)

/** Ktor client for queue TV screen management. */
class KtorDisplayTokenRepository(
    private val httpClient: HttpClient,
    private val serverSettings: ServerSettingsRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : DisplayTokenRepository {

    private suspend fun baseUrl() = serverSettings.getBaseUrl().trimEnd('/')

    override suspend fun list(): Result<List<DisplayScreen>> = withContext(ioDispatcher) {
        try {
            val response = httpClient.get("${baseUrl()}/api/v1/store/display-tokens")
            if (!response.status.isSuccess()) {
                return@withContext Result.failure(HttpErrorMapper.toDomainException(response, "Gagal memuat layar antrean."))
            }
            Result.success(response.body<DisplayTokenListDto>().displayTokens.map { it.toScreen() })
        } catch (t: Throwable) {
            Result.failure(HttpErrorMapper.fromThrowable(t, "Gagal memuat layar antrean."))
        }
    }

    override suspend fun create(name: String): Result<CreatedDisplayScreen> = withContext(ioDispatcher) {
        try {
            val response = httpClient.post("${baseUrl()}/api/v1/store/display-tokens") {
                contentType(ContentType.Application.Json)
                setBody(CreateDisplayRequestDto(name.trim()))
            }
            if (!response.status.isSuccess()) {
                return@withContext Result.failure(HttpErrorMapper.toDomainException(response, "Gagal membuat layar antrean."))
            }
            val dto = response.body<DisplayTokenDto>()
            val token = dto.token ?: return@withContext Result.failure(IllegalStateException("Server tidak mengirim kode layar."))
            Result.success(CreatedDisplayScreen(dto.toScreen(), token, dto.displayUrl.orEmpty()))
        } catch (t: Throwable) {
            Result.failure(HttpErrorMapper.fromThrowable(t, "Gagal membuat layar antrean."))
        }
    }

    override suspend fun revoke(id: String): Result<Unit> = withContext(ioDispatcher) {
        try {
            val response = httpClient.post("${baseUrl()}/api/v1/store/display-tokens/$id/revoke")
            if (!response.status.isSuccess()) {
                return@withContext Result.failure(HttpErrorMapper.toDomainException(response, "Gagal mencabut layar antrean."))
            }
            Result.success(Unit)
        } catch (t: Throwable) {
            Result.failure(HttpErrorMapper.fromThrowable(t, "Gagal mencabut layar antrean."))
        }
    }
}
