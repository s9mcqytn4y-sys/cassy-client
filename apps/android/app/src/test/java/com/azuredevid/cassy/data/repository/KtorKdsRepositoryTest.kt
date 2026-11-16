package com.azuredevid.cassy.data.repository

import com.azuredevid.cassy.domain.error.DomainError
import com.azuredevid.cassy.domain.error.DomainException
import com.azuredevid.cassy.domain.model.KdsStation
import com.azuredevid.cassy.domain.model.KdsTicketStatus
import com.azuredevid.cassy.domain.repository.KdsRepository
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
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KtorKdsRepositoryTest {

    private class FakeServerSettings : ServerSettingsRepository {
        override fun getBaseUrl(): String = "https://demo.cassy.test"
        override fun setBaseUrl(url: String) = Unit
    }

    private val requests = mutableListOf<HttpRequestData>()

    private fun repository(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): KtorKdsRepository {
        val client = HttpClient(MockEngine { request -> requests += request; handler(request) }) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }) }
        }
        return KtorKdsRepository(client, FakeServerSettings(), Dispatchers.Unconfined)
    }

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))

    @Test
    fun listTickets_queriesStationAndFlattensModifierSnapshot() = runTest {
        val options = """[{"group_name":"Gula","option_name":"Less sugar","quantity":1},""" +
            """{"group_name":"Topping","option_name":"Boba","quantity":2,"sub_addons":[{"option_name":"Extra brown sugar","quantity":1}]}]"""
        val repo = repository {
            json(
                """{"tickets":[
                  {"id":"k-1","order_id":"o-1","order_number":"ORD-7","queue_number":"A007","table_number":"5","station":"bar",
                   "status":"queued","late_payment":false,"created_at":"2026-11-16T10:00:00Z",
                   "lines":[{"name":"Kopi Susu","variant":"Large","quantity":2,"options":${JsonPrimitive(options)},"notes":"Tanpa es"}]},
                  {"id":"k-2","order_id":"o-2","station":"grill","status":"queued","late_payment":false,"created_at":"2026-11-16T10:01:00Z","lines":[]}
                ]}"""
            )
        }

        val tickets = repo.listTickets(KdsStation.BAR).getOrThrow()

        assertEquals("bar", requests.single().url.parameters["station"])
        assertEquals("/api/v1/kds/tickets", requests.single().url.encodedPath)
        assertEquals("unknown station is dropped", 1, tickets.size)
        val line = tickets.single().lines.single()
        assertEquals(listOf("Less sugar", "2x Boba", "  Extra brown sugar"), line.options)
        assertEquals("Tanpa es", line.notes)
        assertEquals(1_794_823_200_000L, tickets.single().createdAtMillis)
    }

    @Test
    fun transition_sendsTargetAndExpectedStatus() = runTest {
        val repo = repository {
            json("""{"id":"k-1","order_id":"o-1","station":"kitchen","status":"preparing","late_payment":false,"created_at":"2026-11-16T10:00:00Z","lines":[]}""")
        }

        val updated = repo.transition("k-1", KdsTicketStatus.PREPARING, KdsTicketStatus.QUEUED).getOrThrow()

        val request = requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/api/v1/kds/tickets/k-1/transition", request.url.encodedPath)
        val body = Json.parseToJsonElement(String(request.body.toByteArray())).jsonObject
        assertEquals("preparing", body["to"]!!.jsonPrimitive.content)
        assertEquals("queued", body["expected_status"]!!.jsonPrimitive.content)
        assertEquals(KdsTicketStatus.PREPARING, updated.status)
    }

    @Test
    fun transition_staleStatusSurfacesConflictCode() = runTest {
        val repo = repository {
            json("""{"code":"KDS_STALE_STATUS","error":"Tiket sudah diubah perangkat lain."}""", HttpStatusCode.Conflict)
        }

        val error = (repo.transition("k-1", KdsTicketStatus.READY, KdsTicketStatus.PREPARING).exceptionOrNull() as DomainException).error

        assertTrue(error is DomainError.Conflict)
        assertEquals(KdsRepository.CODE_STALE_STATUS, (error as DomainError.Conflict).code)
    }
}
