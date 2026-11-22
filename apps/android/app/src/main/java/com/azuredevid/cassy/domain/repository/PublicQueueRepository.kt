package com.azuredevid.cassy.domain.repository

import androidx.compose.runtime.Immutable

/** One public queue entry displayed on the outlet TV board. */
@Immutable
data class PublicQueueEntry(
    val queueNumber: String,
    val status: String
)

/** Snapshot returned by `GET /api/v1/public/queue`. */
@Immutable
data class PublicQueueSnapshot(
    val storeName: String,
    val storeTimezone: String,
    val orders: List<PublicQueueEntry>
)

/**
 * Public queue TV board data contract (`GET /api/v1/public/queue` and `POST /api/v1/public/queue/ws-ticket`).
 * Authenticated via `Authorization: Display <displayToken>` issued by the store owner.
 */
interface PublicQueueRepository {
    suspend fun fetchQueue(displayToken: String): Result<PublicQueueSnapshot>
    suspend fun requestWsTicket(displayToken: String): Result<String>
}
