package com.azuredevid.cassy.data.repository

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PosOperationalEndpointsTest {

    private val fakeServerSettings = object : ServerSettingsRepository {
        override fun getBaseUrl(): String = "https://api.abbdullaziz.online"
        override fun setBaseUrl(url: String) = Unit
    }

    private fun createMockHttpClient(
        handler: suspend io.ktor.client.engine.mock.MockRequestHandleScope.(io.ktor.client.request.HttpRequestData) -> io.ktor.client.request.HttpResponseData
    ): HttpClient {
        val engine = MockEngine { request -> handler(request) }
        return HttpClient(engine) {
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        isLenient = true
                        explicitNulls = false
                    }
                )
            }
        }
    }

    @Test
    fun `reopenTab sends POST to pos tabs reopen and maps PosOrder`() = runTest {
        var capturedPath = ""
        var capturedMethod: HttpMethod? = null

        val client = createMockHttpClient { request ->
            capturedPath = request.url.encodedPath
            capturedMethod = request.method
            respond(
                content = """
                    {
                        "order": {
                            "id": "ord-tab-001",
                            "order_number": "TAB-101",
                            "status": "open",
                            "payment_status": "unpaid",
                            "order_type": "dine_in",
                            "table_number": "12",
                            "subtotal_amount": 75000,
                            "tax_amount": 7500,
                            "service_charge_amount": 0,
                            "total_amount": 82500,
                            "items": []
                        }
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }

        val repo = KtorTableTabRepository(
            httpClient = client,
            serverSettings = fakeServerSettings,
            ioDispatcher = UnconfinedTestDispatcher(testScheduler)
        )

        val result = repo.reopenTab("ord-tab-001")
        assertTrue(result.isSuccess)
        assertEquals(HttpMethod.Post, capturedMethod)
        assertEquals("/api/v1/pos/tabs/ord-tab-001/reopen", capturedPath)
        val order = result.getOrThrow()
        assertEquals("ord-tab-001", order.id)
        assertEquals("TAB-101", order.orderNumber)
        assertEquals(82500L, order.totalAmount)
    }

    @Test
    fun `sendDigitalReceipt posts normalized channel and recipient to receipt send endpoint`() = runTest {
        var capturedPath = ""
        var capturedBody = ""

        val client = createMockHttpClient { request ->
            capturedPath = request.url.encodedPath
            capturedBody = (request.body as? TextContent)?.text.orEmpty()
            respond(
                content = """
                    {
                        "channel": "whatsapp",
                        "recipient": "+62812****7890",
                        "expires_at": "2026-11-28T10:00:00Z"
                    }
                """.trimIndent(),
                status = HttpStatusCode.Accepted,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }

        val repo = KtorPosOrderRepository(
            httpClient = client,
            serverSettings = fakeServerSettings,
            ioDispatcher = UnconfinedTestDispatcher(testScheduler)
        )

        val result = repo.sendDigitalReceipt(
            orderId = "ord-999",
            channel = "WhatsApp",
            to = " 081234567890 "
        )

        assertTrue(result.isSuccess)
        assertEquals("/api/v1/pos/orders/ord-999/receipt/send", capturedPath)
        assertTrue(capturedBody.contains("\"channel\":\"whatsapp\""))
        assertTrue(capturedBody.contains("\"to\":\"081234567890\""))
        val sent = result.getOrThrow()
        assertEquals("whatsapp", sent.channel)
        assertEquals("+62812****7890", sent.recipientMasked)
    }

    @Test
    fun `listSalesChannels parses active and inactive channels with variant price overrides`() = runTest {
        var capturedPath = ""

        val client = createMockHttpClient { request ->
            capturedPath = request.url.encodedPath
            respond(
                content = """
                    {
                        "sales_channels": [
                            {
                                "id": "ch-gofood",
                                "name": "GoFood",
                                "commission_bps": 2000,
                                "is_active": true,
                                "prices": [
                                    {
                                        "variant_id": "var-kopi-susu",
                                        "price": 28000
                                    }
                                ]
                            },
                            {
                                "id": "ch-grabfood",
                                "name": "GrabFood",
                                "commission_bps": 2000,
                                "is_active": false,
                                "prices": []
                            }
                        ]
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }

        val repo = KtorPosOrderRepository(
            httpClient = client,
            serverSettings = fakeServerSettings,
            ioDispatcher = UnconfinedTestDispatcher(testScheduler)
        )

        val result = repo.listSalesChannels()
        assertTrue(result.isSuccess)
        assertEquals("/api/v1/pos/sales-channels", capturedPath)
        val channels = result.getOrThrow()
        assertEquals(2, channels.size)
        assertEquals("ch-gofood", channels[0].id)
        assertEquals("GoFood", channels[0].name)
        assertEquals(2000L, channels[0].commissionBps)
        assertTrue(channels[0].isActive)
        assertEquals(1, channels[0].prices.size)
        assertEquals(28000L, channels[0].prices[0].priceRupiah)
    }

    @Test
    fun `lookupCustomerByPhone posts phone and maps loyalty stamp balance`() = runTest {
        var capturedPath = ""
        var capturedBody = ""

        val client = createMockHttpClient { request ->
            capturedPath = request.url.encodedPath
            capturedBody = (request.body as? TextContent)?.text.orEmpty()
            respond(
                content = """
                    {
                        "customer": {
                            "id": "cust-123",
                            "phone": "+62811223344",
                            "name": "Budi Santoso"
                        },
                        "verified": true,
                        "program_active": true,
                        "stamps": 8,
                        "stamps_required": 10,
                        "reward_ready": false,
                        "reward_product_ids": ["prod-americano"]
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }

        val repo = KtorPosOrderRepository(
            httpClient = client,
            serverSettings = fakeServerSettings,
            ioDispatcher = UnconfinedTestDispatcher(testScheduler)
        )

        val result = repo.lookupCustomerByPhone(" 0811223344 ")
        assertTrue(result.isSuccess)
        assertEquals("/api/v1/pos/customers/lookup", capturedPath)
        assertTrue(capturedBody.contains("\"phone\":\"0811223344\""))
        val stamps = result.getOrThrow()
        assertEquals("cust-123", stamps.customerId)
        assertEquals("Budi Santoso", stamps.name)
        assertTrue(stamps.verified)
        assertTrue(stamps.programActive)
        assertEquals(8L, stamps.stamps)
        assertEquals(10L, stamps.stampsRequired)
        assertEquals(listOf("prod-americano"), stamps.rewardProductIds)
    }
}
