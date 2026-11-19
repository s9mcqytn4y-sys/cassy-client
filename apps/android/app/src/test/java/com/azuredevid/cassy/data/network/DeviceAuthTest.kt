package com.azuredevid.cassy.data.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceAuthTest {

    @Test
    fun deviceCallKeepsItsHeaderAndNeverRefreshesTheStaffSession() = runTest {
        val seen = mutableListOf<String?>()
        var refreshed = 0
        val client = HttpClient(MockEngine { request ->
            seen += request.headers[HttpHeaders.Authorization]
            respond("", HttpStatusCode.Unauthorized)
        }) {
            install(Auth) {
                bearer {
                    loadTokens { BearerTokens("staff-jwt", "") }
                    refreshTokens {
                        refreshed++
                        BearerTokens("staff-jwt-2", "")
                    }
                    sendWithoutRequest { carriesStaffBearer(it.url.build().encodedPath) }
                }
            }
        }

        val status = client.get("https://api.test/api/v1/device/catalog") { deviceAuth("s.d.secret") }.status

        assertEquals(HttpStatusCode.Unauthorized, status)
        assertEquals(listOf<String?>("Device s.d.secret"), seen)
        assertEquals(0, refreshed)
    }

    @Test
    fun staffCallsStillCarryTheBearer() {
        assertEquals(true, carriesStaffBearer("/api/v1/pos/orders"))
        assertEquals(false, carriesStaffBearer("/api/v1/device/offline-orders/sync"))
        assertEquals(false, carriesStaffBearer("/api/v1/public/queue"))
        assertEquals(false, carriesStaffBearer("/api/v1/auth/staff/refresh"))
    }
}
