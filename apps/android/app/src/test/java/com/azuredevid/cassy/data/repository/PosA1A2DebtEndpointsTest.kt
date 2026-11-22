package com.azuredevid.cassy.data.repository

import com.azuredevid.cassy.core.security.SessionManager
import com.azuredevid.cassy.domain.repository.PosOrderDiscountInput
import com.azuredevid.cassy.domain.repository.PosOrderVoidInput
import com.azuredevid.cassy.domain.repository.PosRefundPaymentInput
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
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
class PosA1A2DebtEndpointsTest {

    private val fakeServerSettings = object : ServerSettingsRepository {
        override fun getBaseUrl(): String = "https://api.abbdullaziz.online"
        override fun setBaseUrl(url: String) = Unit
    }

    private fun createMockHttpClient(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData
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

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    @Test
    fun `table session lifecycle endpoints post exact payloads for move merge split extend close`() = runTest {
        val calls = mutableListOf<Pair<String, String>>()
        val client = createMockHttpClient { request ->
            calls.add(request.url.encodedPath to (request.body as? TextContent)?.text.orEmpty())
            respond(
                content = """{"id":"sess-1","table_id":"tbl-2","status":"active","expires_at":"2026-11-21T15:00:00Z"}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders
            )
        }
        val repo = KtorTableTabRepository(client, fakeServerSettings, UnconfinedTestDispatcher(testScheduler))

        assertTrue(repo.moveTableSession("sess-1", "tbl-2").isSuccess)
        assertTrue(repo.mergeTableSession("sess-1", "sess-2").isSuccess)
        assertTrue(repo.splitTableSession("sess-1", "tbl-3").isSuccess)
        assertTrue(repo.extendTableSession("sess-1", 30).isSuccess)
        assertTrue(repo.closeTableSession("sess-1").isSuccess)

        assertEquals("/api/v1/pos/table-sessions/sess-1/move", calls[0].first)
        assertTrue(calls[0].second.contains("\"to_table_id\":\"tbl-2\""))
        assertEquals("/api/v1/pos/table-sessions/sess-1/merge", calls[1].first)
        assertTrue(calls[1].second.contains("\"session_id\":\"sess-2\""))
        assertEquals("/api/v1/pos/table-sessions/sess-1/split", calls[2].first)
        assertTrue(calls[2].second.contains("\"table_id\":\"tbl-3\""))
        assertEquals("/api/v1/pos/table-sessions/sess-1/extend", calls[3].first)
        assertTrue(calls[3].second.contains("\"minutes\":30"))
        assertEquals("/api/v1/pos/table-sessions/sess-1/close", calls[4].first)
    }

    @Test
    fun `post settlement order actions hit discount void refund reprint receipt-link and stock-override`() = runTest {
        val paths = mutableListOf<String>()
        val client = createMockHttpClient { request ->
            paths.add(request.url.encodedPath)
            val body = when {
                request.url.encodedPath.endsWith("/reprint") -> """{"jobs":2}"""
                request.url.encodedPath.endsWith("/receipt-link") ->
                    """{"url":"https://pos.abbdullaziz.online/r/ABC123","short_code":"ABC123","expires_at":"2027-02-20T00:00:00Z"}"""
                request.url.encodedPath.endsWith("/stock-override") ->
                    """{"product_id":"prod-1","product_name":"Croissant","previous_quantity":2,"new_quantity":15,"delta":13}"""
                request.url.encodedPath.endsWith("/refund") ->
                    """{"message":"Refund diproses","refund_status":"completed"}"""
                else -> """{"order":{"id":"ord-1","order_number":"ORD-101","status":"open","payment_status":"unpaid","order_type":"dine_in","subtotal_amount":50000,"tax_amount":5000,"total_amount":55000,"items":[]}}"""
            }
            respond(content = body, status = HttpStatusCode.OK, headers = jsonHeaders)
        }
        val repo = KtorPosOrderRepository(client, fakeServerSettings, UnconfinedTestDispatcher(testScheduler))

        assertTrue(repo.applyOrderDiscount("ord-1", PosOrderDiscountInput(discountAmount = 5000L, reason = "Promo staff")).isSuccess)
        assertTrue(repo.voidOrder("ord-1", PosOrderVoidInput(reason = "Salah input", pin = "123456")).isSuccess)
        assertEquals("completed", repo.refundPayment("ord-1", "pay-1", PosRefundPaymentInput(reason = "Batal", pin = "123456", restoreStock = true)).getOrThrow())
        assertEquals(2, repo.reprintOrder("ord-1", "ticket").getOrThrow())
        assertEquals("ABC123", repo.issueReceiptLink("ord-1").getOrThrow().shortCode)
        assertEquals(13, repo.overrideProductStock("prod-1", 15, "Stok pagi", "654321").getOrThrow().delta)

        assertEquals(
            listOf(
                "/api/v1/pos/orders/ord-1/discount",
                "/api/v1/pos/orders/ord-1/void",
                "/api/v1/pos/orders/ord-1/payments/pay-1/refund",
                "/api/v1/pos/orders/ord-1/reprint",
                "/api/v1/pos/orders/ord-1/receipt-link",
                "/api/v1/pos/products/prod-1/stock-override"
            ),
            paths
        )
    }

    @Test
    fun `customer verification start and confirm endpoints map verification id and stamps`() = runTest {
        val paths = mutableListOf<String>()
        val client = createMockHttpClient { request ->
            paths.add(request.url.encodedPath)
            if (request.url.encodedPath.endsWith("/verify")) {
                respond(
                    content = """{"id":"ver-99","expires_at":"2026-11-21T13:05:00Z"}""",
                    status = HttpStatusCode.Accepted,
                    headers = jsonHeaders
                )
            } else {
                respond(
                    content = """{"customer":{"id":"c-1","phone":"+62812345678","name":"Rina"},"verified":true,"program_active":true,"stamps":3,"stamps_required":10,"reward_ready":false,"reward_product_ids":[]}""",
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders
                )
            }
        }
        val repo = KtorPosOrderRepository(client, fakeServerSettings, UnconfinedTestDispatcher(testScheduler))

        val start = repo.startCustomerVerification("0812345678", "Rina").getOrThrow()
        assertEquals("ver-99", start.verificationId)
        val confirmed = repo.confirmCustomerVerification("ver-99", "482910").getOrThrow()
        assertTrue(confirmed.verified)
        assertEquals(3L, confirmed.stamps)
        assertEquals(listOf("/api/v1/pos/customers/verify", "/api/v1/pos/customers/verify/ver-99/confirm"), paths)
    }

    @Test
    fun `shift force close and drawer patch hit expected endpoints and methods`() = runTest {
        val calls = mutableListOf<Pair<HttpMethod, String>>()
        val client = createMockHttpClient { request ->
            calls.add(request.method to request.url.encodedPath)
            if (request.method == HttpMethod.Patch) {
                respond(
                    content = """{"id":"drw-1","store_id":"store-1","name":"Kasir Utama 2","is_active":true}""",
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders
                )
            } else {
                respond(
                    content = """{"shift_id":"shf-1","store_id":"store-1","drawer_id":"drw-1","drawer_name":"Kasir Utama","opened_by":"u-1","opened_by_name":"Kasir","opened_at":"2026-11-21T08:00:00Z","closed_at":"2026-11-21T12:00:00Z","expected_cash":300000,"counted_cash":300000,"variance":0}""",
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders
                )
            }
        }
        val repo = KtorShiftRepository(client, fakeServerSettings, UnconfinedTestDispatcher(testScheduler))

        val zReport = repo.forceCloseShift("shf-1", "Kasir pulang darurat", 300000L, "Ditutup manager").getOrThrow()
        assertEquals("shf-1", zReport.shiftId)
        val drawer = repo.updateDrawer("drw-1", "Kasir Utama 2", true).getOrThrow()
        assertEquals("Kasir Utama 2", drawer.name)

        assertEquals(HttpMethod.Post to "/api/v1/pos/shifts/shf-1/force-close", calls[0])
        assertEquals(HttpMethod.Patch to "/api/v1/pos/drawers/drw-1", calls[1])
    }

    @Test
    fun `staff pin self-service endpoints cover change forgot forgot-sms reset-sms and setup`() = runTest {
        val paths = mutableListOf<String>()
        val client = createMockHttpClient { request ->
            paths.add(request.url.encodedPath)
            when {
                request.url.encodedPath.endsWith("/pin/change") ->
                    respond(content = "", status = HttpStatusCode.NoContent)
                request.url.encodedPath.endsWith("/pin/forgot") || request.url.encodedPath.endsWith("/pin/forgot-sms") ->
                    respond(content = """{"message":"Terkirim"}""", status = HttpStatusCode.Accepted, headers = jsonHeaders)
                else ->
                    respond(
                        content = """{"message":"PIN berhasil dibuat","store_slug":"central-cafe","email":"kasir@cassy.id"}""",
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders
                    )
            }
        }
        val dummySession = object : SessionManager {
            override val sessionFlow = kotlinx.coroutines.flow.MutableStateFlow<com.azuredevid.cassy.domain.model.StoredSession?>(null)
            override fun currentSession() = null
            override fun getAccessToken() = null
            override fun getStoreSlug() = null
            override fun getDeviceCredential() = null
            override suspend fun awaitLoaded() = null
            override suspend fun saveLoginSession(
                accessToken: String,
                accessTokenExpiresAt: String,
                sessionExpiresAt: String,
                staff: com.azuredevid.cassy.domain.model.StaffIdentity,
                permissions: List<String>,
                pinChangeRequired: Boolean,
                deviceCredential: String?,
                offlinePinVerifier: com.azuredevid.cassy.domain.model.OfflinePinVerifier?
            ) = Unit
            override suspend fun updateRefreshedTokens(
                accessToken: String,
                accessTokenExpiresAt: String,
                sessionExpiresAt: String,
                staff: com.azuredevid.cassy.domain.model.StaffIdentity?,
                permissions: List<String>?
            ) = Unit
            override suspend fun saveCookies(cookies: List<com.azuredevid.cassy.domain.model.StoredCookie>) = Unit
            override suspend fun getCookies() = emptyList<com.azuredevid.cassy.domain.model.StoredCookie>()
            override suspend fun clearSession(preserveDeviceBinding: Boolean) = Unit
        }
        val repo = KtorAuthRepository(client, fakeServerSettings, dummySession, UnconfinedTestDispatcher(testScheduler))

        assertTrue(repo.changePin("111111", "222222").isSuccess)
        assertEquals("Terkirim", repo.forgotPinByEmail("central-cafe", "kasir@cassy.id").getOrThrow())
        assertEquals("Terkirim", repo.forgotPinBySms("central-cafe", "08123456789").getOrThrow())
        assertEquals("central-cafe", repo.resetPinWithSms("central-cafe", "08123456789", "123456", "654321").getOrThrow().storeSlug)
        assertEquals("kasir@cassy.id", repo.setupPinWithToken("tok-abc", "654321").getOrThrow().email)

        assertEquals(
            listOf(
                "/api/v1/auth/staff/pin/change",
                "/api/v1/auth/staff/pin/forgot",
                "/api/v1/auth/staff/pin/forgot-sms",
                "/api/v1/auth/staff/pin/reset-sms",
                "/api/v1/auth/staff/pin/setup"
            ),
            paths
        )
    }
}
