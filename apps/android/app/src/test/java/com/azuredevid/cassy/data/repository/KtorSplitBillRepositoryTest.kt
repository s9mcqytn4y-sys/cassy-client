package com.azuredevid.cassy.data.repository

import com.azuredevid.cassy.domain.error.DomainError
import com.azuredevid.cassy.domain.error.DomainException
import com.azuredevid.cassy.domain.model.ItemAllocation
import com.azuredevid.cassy.domain.model.ShareAllocation
import com.azuredevid.cassy.domain.model.SplitPlan
import com.azuredevid.cassy.domain.repository.SplitBillRepository
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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KtorSplitBillRepositoryTest {

    private class FakeServerSettings : ServerSettingsRepository {
        override fun getBaseUrl(): String = "https://demo.cassy.test/"
        override fun setBaseUrl(url: String) = Unit
    }

    private val requests = mutableListOf<HttpRequestData>()

    private fun repository(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): KtorSplitBillRepository {
        val client = HttpClient(MockEngine { request -> requests += request; handler(request) }) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = false; explicitNulls = false }) }
        }
        return KtorSplitBillRepository(client, FakeServerSettings(), Dispatchers.Unconfined)
    }

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))

    private val orderJson = """{"id":"ord-1","order_number":"ORD-9","payment_status":"pending","total_amount":90001,"paid_amount":0}"""
    private val splitsJson = """[
        {"id":"sp-2","split_number":2,"label":"Split 2/2","split_mode":"equal","total_amount":45000,"paid_amount":0,"status":"unpaid"},
        {"id":"sp-1","split_number":1,"label":"Split 1/2","split_mode":"equal","total_amount":45001,"paid_amount":0,"status":"unpaid"}
    ]"""

    @Test
    fun divideEqual_postsStrategyAndKeepsServerAmounts() = runTest {
        val repo = repository { json("""{"order":$orderJson,"splits":$splitsJson}""") }

        val bill = repo.divide("ord-1", SplitPlan.Equal(2)).getOrThrow()

        val request = requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/api/v1/pos/orders/ord-1/splits", request.url.encodedPath)
        val body = Json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject
        assertEquals("equal", body["strategy"]!!.jsonPrimitive.content)
        assertEquals(2, body["split_count"]!!.jsonPrimitive.int)
        assertFalse("no item list for an equal split", body.containsKey("splits"))
        assertEquals(listOf("sp-1", "sp-2"), bill.shares.map { it.id })
        assertEquals("rounding stays where the server put it", listOf(45_001L, 45_000L), bill.shares.map { it.totalAmount })
    }

    @Test
    fun divideByItem_sendsTrimmedLabelsAndQuantities() = runTest {
        val repo = repository { json("""{"order":$orderJson,"splits":$splitsJson}""") }

        repo.divide(
            "ord-1",
            SplitPlan.ByItem(
                listOf(
                    ShareAllocation(" Andi ", listOf(ItemAllocation("it-1", 2))),
                    ShareAllocation("Sari", listOf(ItemAllocation("it-2", 1)))
                )
            )
        ).getOrThrow()

        val body = Json.parseToJsonElement(requests.single().body.toByteArray().decodeToString()).jsonObject
        assertEquals("by_item", body["strategy"]!!.jsonPrimitive.content)
        val shares = body["splits"]!!.jsonArray
        assertEquals("Andi", shares[0].jsonObject["label"]!!.jsonPrimitive.content)
        val item = shares[0].jsonObject["items"]!!.jsonArray.single().jsonObject
        assertEquals("it-1", item["order_item_id"]!!.jsonPrimitive.content)
        assertEquals(2, item["quantity"]!!.jsonPrimitive.int)
    }

    @Test
    fun divideConflict_mapsSplitNotAllowedCode() = runTest {
        val repo = repository {
            json("""{"error":"Order cannot be split","code":"SPLIT_NOT_ALLOWED"}""", HttpStatusCode.Conflict)
        }

        val error = (repo.divide("ord-1", SplitPlan.Equal(2)).exceptionOrNull() as DomainException).error

        assertTrue(error is DomainError.Conflict)
        assertEquals(SplitBillRepository.CODE_SPLIT_NOT_ALLOWED, (error as DomainError.Conflict).code)
    }

    @Test
    fun getBillAndOpenBills_hitOrderReadRoutes() = runTest {
        val repo = repository { request ->
            if (request.url.encodedPath.endsWith("/orders")) {
                json("""{"items":[{"id":"ord-1","order_number":"ORD-9","table_number":"5","total_amount":90000,"paid_amount":30000}],"next_cursor":null}""")
            } else {
                json("""{"order":$orderJson,"splits":$splitsJson,"payments":[],"refunds":[],"rounds":[],"allowed_actions":[]}""")
            }
        }

        val bill = repo.getBill("ord-1").getOrThrow()
        val open = repo.listOpenBills(" meja 5 ").getOrThrow()

        assertEquals("/api/v1/pos/orders/ord-1", requests[0].url.encodedPath)
        assertEquals(2, bill.shares.size)
        assertEquals("partially_paid", requests[1].url.parameters["payment_status"])
        assertEquals("meja 5", requests[1].url.parameters["q"])
        assertEquals(60_000L, open.single().remainingAmount)
    }
}
