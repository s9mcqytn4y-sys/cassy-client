package com.azuredevid.cassy.core.util

import io.ktor.http.encodeURLPathPart

/**
 * Builds the guest ordering URL printed on a table QR: `<origin>/order/<qr_token>`.
 *
 * The origin is the server address the tablet already talks to. Under the single-host topology
 * (ADR-002) Nginx serves the guest web app at `/` and the API at `/api/` on that origin, which matches
 * the backend's own `publicURL + "/order/" + token` link. Returns null for a blank token or an origin
 * that is not http(s), so callers never print a QR that cannot resolve.
 */
object TableQrUrl {
    fun build(serverBaseUrl: String, qrToken: String?): String? {
        val origin = serverBaseUrl.trim().trimEnd('/')
        val token = qrToken?.trim().orEmpty()
        if (token.isEmpty()) return null
        if (!origin.startsWith("https://") && !origin.startsWith("http://")) return null
        if (origin.substringAfter("://").isBlank()) return null
        return "$origin/order/${token.encodeURLPathPart()}"
    }
}
