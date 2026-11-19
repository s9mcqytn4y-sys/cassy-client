package com.azuredevid.cassy.data.repository

import com.azuredevid.cassy.domain.error.DomainError
import com.azuredevid.cassy.domain.error.DomainException
import com.azuredevid.cassy.domain.model.TableDraft
import com.azuredevid.cassy.domain.model.TableStatus
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class KtorTableManagementRepositoryTest {

    private class FakeServerSettings(private var url: String = "https://demo.cassy.test/") : ServerSettingsRepository {
        override fun getBaseUrl(): String = url
        override fun setBaseUrl(url: String) { this.url = url }
    }

    private val requests = mutableListOf<HttpRequestData>()

    private fun repository(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): KtorTableManagementRepository {
        val engine = MockEngine { request ->
            requests += request
            handler(request)
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true })
            }
        }
        return KtorTableManagementRepository(client, FakeServerSettings(), Dispatchers.Unconfined)
    }

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))

    private val boardJson = """
        {"tables": [
          {"id": "t-1", "table_number": "1", "label": "", "section": "Teras", "capacity": 4,
           "qr_token": "tok-one", "qr_url": "https://pos.cassy.test/order/tok-one", "qr_rotated_at": "2026-11-10T08:00:00Z", "is_active": true,
           "created_at": "x", "updated_at": "x", "status": "available", "unpaid_orders": 0, "unpaid_amount": 0},
          {"id": "t-2", "table_number": "2", "label": "Sofa", "section": "", "capacity": 6,
           "qr_rotated_at": "2026-11-10T08:00:00Z", "is_active": true, "created_at": "x", "updated_at": "x",
           "status": "occupied", "session": {"id": "s-1", "table_id": "t-2", "status": "open", "opened_at": "x", "opened_via": "qr"},
           "unpaid_orders": 2, "unpaid_amount": 54000, "timer_state": "none"},
          {"id": "t-3", "table_number": "3", "qr_rotated_at": "x", "is_active": true, "created_at": "x", "updated_at": "x",
           "status": "brand_new_state", "unpaid_orders": 0, "unpaid_amount": 0, "merged_into_table_id": "t-2"}
        ]}
    """.trimIndent()

    @Test
    fun listTables_mapsBoardWithServerDerivedStatus() = runTest {
        val repo = repository { json(boardJson) }

        val tables = repo.listTables().getOrThrow()

        assertEquals("https://demo.cassy.test/api/v1/pos/tables", requests.single().url.toString())
        assertEquals(HttpMethod.Get, requests.single().method)
        assertEquals(3, tables.size)
        with(tables[0]) {
            assertEquals(TableStatus.AVAILABLE, status)
            assertFalse(hasOpenSession)
            assertEquals("tok-one", qrToken)
            assertEquals("https://pos.cassy.test/order/tok-one", qrUrl)
            assertNull(qrRotationBlock())
        }
        with(tables[1]) {
            assertEquals("Sofa", displayName)
            assertEquals(TableStatus.OCCUPIED, status)
            assertTrue(hasOpenSession)
            assertNull("qr_token is omitted without table.qr.manage", qrToken)
            assertNull(qrUrl)
            assertEquals(2, unpaidOrders)
        }
        with(tables[2]) {
            assertEquals("unknown status fails closed", TableStatus.OCCUPIED, status)
            assertEquals("t-2", mergedIntoTableId)
        }
    }

    @Test
    fun createTable_postsTrimmedInputAndReturnsQrToken() = runTest {
        val repo = repository {
            json("""{"id":"t-9","table_number":"A3","label":"Bar","section":"","capacity":2,"qr_token":"fresh",
                "qr_rotated_at":"2026-11-16T00:00:00Z","is_active":true,"created_at":"x","updated_at":"x"}""",
                HttpStatusCode.Created)
        }

        val created = repo.createTable(TableDraft(tableNumber = " A3 ", label = " Bar ", capacity = 2)).getOrThrow()

        val request = requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/api/v1/pos/tables", request.url.encodedPath)
        val body = Json.parseToJsonElement(String(request.body.toByteArray())).jsonObject
        assertEquals("A3", body["table_number"]!!.jsonPrimitive.content)
        assertEquals("Bar", body["label"]!!.jsonPrimitive.content)
        assertEquals(2, body["capacity"]!!.jsonPrimitive.int)
        assertEquals("fresh", created.qrToken)
        assertEquals(TableStatus.AVAILABLE, created.status)
    }

    @Test
    fun updateTable_patchesEncodedPath() = runTest {
        val repo = repository {
            json("""{"id":"t/1","table_number":"1","qr_rotated_at":"x","is_active":false,"created_at":"x","updated_at":"x"}""")
        }

        val updated = repo.updateTable("t/1", TableDraft(tableNumber = "1", isActive = false)).getOrThrow()

        assertEquals(HttpMethod.Patch, requests.single().method)
        assertEquals("/api/v1/pos/tables/t%2F1", requests.single().url.encodedPath)
        assertFalse(updated.isActive)
    }

    @Test
    fun rotateQr_postsToRotateEndpoint() = runTest {
        val repo = repository {
            json("""{"id":"t-1","table_number":"1","qr_token":"rotated","qr_url":"javascript:alert(1)","qr_rotated_at":"2026-11-16T10:00:00Z",
                "is_active":true,"created_at":"x","updated_at":"x"}""")
        }

        val rotated = repo.rotateQr("t-1").getOrThrow()

        assertEquals(HttpMethod.Post, requests.single().method)
        assertEquals("/api/v1/pos/tables/t-1/rotate-qr", requests.single().url.encodedPath)
        assertEquals("rotated", rotated.qrToken)
        assertNull("a non-http qr_url is never printed", rotated.qrUrl)
    }

    @Test
    fun archiveTable_conflictCarriesBackendCodeAndMessage() = runTest {
        val repo = repository {
            json("""{"code":"TABLE_SESSION_OPEN","error":"Meja masih memiliki sesi aktif."}""", HttpStatusCode.Conflict)
        }

        val error = (repo.archiveTable("t-2").exceptionOrNull() as DomainException).error

        assertEquals("/api/v1/pos/tables/t-2/archive", requests.single().url.encodedPath)
        assertTrue(error is DomainError.Conflict)
        assertEquals("TABLE_SESSION_OPEN", (error as DomainError.Conflict).code)
        assertEquals("Meja masih memiliki sesi aktif.", error.message)
    }

    @Test
    fun archiveTable_noContentSucceeds() = runTest {
        val repo = repository { respond("", HttpStatusCode.NoContent) }
        assertTrue(repo.archiveTable("t-1").isSuccess)
    }

    @Test
    fun createTable_planLimitSurfacesAs402WithServerText() = runTest {
        val repo = repository {
            json("""{"code":"PLAN_LIMIT_REACHED","error":"Paket Anda mencakup maksimal 15 meja aktif.","resource":"tables","limit":15,"plan_code":"starter_umkm"}""",
                HttpStatusCode.PaymentRequired)
        }

        val error = (repo.createTable(TableDraft("16")).exceptionOrNull() as DomainException).error

        assertTrue(error is DomainError.ServerError)
        assertEquals(402, (error as DomainError.ServerError).statusCode)
        assertEquals("Paket Anda mencakup maksimal 15 meja aktif.", error.message)
    }

    @Test
    fun listTables_networkFailureMapsToNetworkUnavailable() = runTest {
        val repo = repository { throw IOException("socket closed") }

        val error = (repo.listTables().exceptionOrNull() as DomainException).error

        assertTrue(error is DomainError.NetworkUnavailable)
    }

    @Test
    fun listTables_malformedBodyIsAFailureNotACrash() = runTest {
        val repo = repository { json("""{"tables": [{"id": 5}]}""") }
        assertTrue(repo.listTables().isFailure)
    }
}
