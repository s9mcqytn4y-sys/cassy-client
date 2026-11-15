package com.azuredevid.cassy.data.network

import com.azuredevid.cassy.data.repository.ServerSettingsRepository
import com.azuredevid.cassy.domain.repository.ServerConnectionStatus
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class AndroidServerHealthMonitorTest {

    private class FakeServerSettings(private var url: String) : ServerSettingsRepository {
        override fun getBaseUrl(): String = url
        override fun setBaseUrl(url: String) {
            this.url = url
        }
    }

    @Test
    fun probeNow_returnsOnlineWithLatencyWhenHealthEndpointReturns200() = runTest {
        var requestedUrl = ""
        val engine = MockEngine { request ->
            requestedUrl = request.url.toString()
            respond(
                content = """{"status":"ok"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val monitor = AndroidServerHealthMonitor(
            context = null,
            httpClient = HttpClient(engine),
            serverSettings = FakeServerSettings("https://demo.abbdullaziz.online/")
        )

        val state = monitor.probeNow()

        assertEquals("https://demo.abbdullaziz.online/api/v1/health", requestedUrl)
        assertEquals(ServerConnectionStatus.ONLINE, state.status)
        assertNotNull(state.latencyMs)
    }

    @Test
    fun probeNow_returnsServerUnreachableWhenHealthEndpointFailsOrThrows() = runTest {
        val errorEngine = MockEngine {
            respond(
                content = "Service Unavailable",
                status = HttpStatusCode.ServiceUnavailable
            )
        }
        val failingMonitor = AndroidServerHealthMonitor(
            context = null,
            httpClient = HttpClient(errorEngine),
            serverSettings = FakeServerSettings("https://demo.abbdullaziz.online")
        )

        val httpErrorState = failingMonitor.probeNow()
        assertEquals(ServerConnectionStatus.SERVER_UNREACHABLE, httpErrorState.status)
        assertNull(httpErrorState.latencyMs)

        val exceptionEngine = MockEngine { throw IOException("timeout") }
        val networkErrorMonitor = AndroidServerHealthMonitor(
            context = null,
            httpClient = HttpClient(exceptionEngine),
            serverSettings = FakeServerSettings("https://demo.abbdullaziz.online")
        )

        val exceptionState = networkErrorMonitor.probeNow()
        assertEquals(ServerConnectionStatus.SERVER_UNREACHABLE, exceptionState.status)
        assertNull(exceptionState.latencyMs)
    }

    @Test
    fun observeConnectivity_emitsCheckingThenOnline() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"status":"ok"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val monitor = AndroidServerHealthMonitor(
            context = null,
            httpClient = HttpClient(engine),
            serverSettings = FakeServerSettings("https://demo.abbdullaziz.online"),
            pollIntervalMs = 60_000L
        )

        val emissions = monitor.observeConnectivity().take(2).toList()

        assertEquals(ServerConnectionStatus.CHECKING, emissions[0].status)
        assertEquals(ServerConnectionStatus.ONLINE, emissions[1].status)
    }

    @Test
    fun isLocalOrPrivateEndpoint_recognizesLanEmulatorAndMdnsHosts() {
        assertTrue(AndroidServerHealthMonitor.isLocalOrPrivateEndpoint("http://192.168.1.100:8080"))
        assertTrue(AndroidServerHealthMonitor.isLocalOrPrivateEndpoint("http://10.0.2.2:8080"))
        assertTrue(AndroidServerHealthMonitor.isLocalOrPrivateEndpoint("http://127.0.0.1:8080"))
        assertTrue(AndroidServerHealthMonitor.isLocalOrPrivateEndpoint("http://localhost:8080"))
        assertTrue(AndroidServerHealthMonitor.isLocalOrPrivateEndpoint("https://pos-lokal.cassy.local"))
        assertTrue(AndroidServerHealthMonitor.isLocalOrPrivateEndpoint("http://172.18.0.3:8080"))

        assertFalse(AndroidServerHealthMonitor.isLocalOrPrivateEndpoint("https://demo.abbdullaziz.online"))
        assertFalse(AndroidServerHealthMonitor.isLocalOrPrivateEndpoint("https://172.32.0.1:8443"))
    }
}
