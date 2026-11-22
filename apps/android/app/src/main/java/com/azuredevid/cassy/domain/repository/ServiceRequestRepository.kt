package com.azuredevid.cassy.domain.repository

/** A guest "Panggil Pelayan" request as staff see it; [createdAt] is the server RFC 3339 timestamp. */
data class ServiceRequest(
    val id: String,
    val tableNumber: String,
    val label: String,
    val note: String?,
    val status: String,
    val createdAt: String
) {
    val isAcknowledged: Boolean get() = status == "acknowledged"
}

/** Staff side of guest service requests (`/api/v1/store/service-requests`). */
interface ServiceRequestRepository {
    /** Open and acknowledged requests, oldest first, so the longest wait sits at the top. */
    suspend fun listActive(): Result<List<ServiceRequest>>

    suspend fun acknowledge(id: String): Result<ServiceRequest>

    suspend fun markDone(id: String): Result<ServiceRequest>

    suspend fun cancelAndMute(id: String): Result<ServiceRequest>
}
