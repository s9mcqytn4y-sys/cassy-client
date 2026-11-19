package com.azuredevid.cassy.data.network

import io.ktor.client.plugins.auth.AuthCircuitBreaker
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders

/**
 * Whether the staff bearer goes on a request to the API host: not on public guest calls, not on the
 * login and refresh calls, and not on device calls, whose `Authorization: Device` it would overwrite.
 */
internal fun carriesStaffBearer(path: String): Boolean =
    !path.startsWith("/api/v1/public/") &&
        !path.startsWith("/api/v1/device/") &&
        !path.endsWith("/auth/staff/login") &&
        !path.endsWith("/auth/staff/refresh")

/**
 * Authenticates a `/api/v1/device/...` call with the paired device credential (`Authorization: Device`).
 * The circuit breaker keeps the staff bearer plugin away: it must neither replace this header nor
 * refresh (or clear) the staff session when the server rejects the device.
 */
fun HttpRequestBuilder.deviceAuth(credential: String) {
    attributes.put(AuthCircuitBreaker, Unit)
    header(HttpHeaders.Authorization, "Device $credential")
}
