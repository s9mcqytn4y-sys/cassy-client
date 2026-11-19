package com.azuredevid.cassy.domain.repository

/** A queue TV screen registered by the owner. The screen never holds a staff credential. */
data class DisplayScreen(
    val id: String,
    val name: String,
    val createdAt: String,
    val revokedAt: String? = null,
    val lastSeenAt: String? = null
) {
    val isActive: Boolean get() = revokedAt == null
}

/** A new screen: [token] and [displayUrl] are shown once; the server keeps only a hash. */
data class CreatedDisplayScreen(val screen: DisplayScreen, val token: String, val displayUrl: String)

/** Owner management of queue TV screens (`/api/v1/store/display-tokens`, store.settings.manage). */
interface DisplayTokenRepository {
    suspend fun list(): Result<List<DisplayScreen>>
    suspend fun create(name: String): Result<CreatedDisplayScreen>
    suspend fun revoke(id: String): Result<Unit>
}
