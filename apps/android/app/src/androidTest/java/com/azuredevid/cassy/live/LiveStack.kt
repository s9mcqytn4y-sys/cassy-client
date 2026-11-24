package com.azuredevid.cassy.live

import com.azuredevid.cassy.data.repository.ServerSettingsRepository
import com.azuredevid.cassy.domain.model.CashDrawer
import com.azuredevid.cassy.domain.repository.AuthRepository
import com.azuredevid.cassy.domain.repository.ShiftRepository
import org.koin.core.context.GlobalContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Shared access to the Docker test stack and the `e2e-kasir` store
 * (cassy-backend db/seeds/E2eLifecycle.sql) for the live instrumentation tests.
 */
internal object LiveStack {
    const val HOST_URL = "http://10.0.2.2"
    const val PRINTER_JOBS = "$HOST_URL:9101/jobs"
    const val CASHIER_PIN = "528307"
    const val MANAGER_PIN = "739164"
    const val KITCHEN_PIN = "615842"

    /** Second Toxiproxy proxy that tests never cut, for staff calls made while `cassy_api` is down (S3-Q2). */
    private const val DIRECT_API = "$HOST_URL:18081/api/v1"
    private const val TOXIPROXY = "$HOST_URL:8474/proxies/cassy_api"

    private val koin get() = GlobalContext.get()

    suspend fun loginCashier() = login("cashier.e2e@cassy.local", CASHIER_PIN)

    suspend fun loginManager() = login("manager.e2e@cassy.local", MANAGER_PIN)

    /** Logs the app in as the kitchen tablet (S3-Q3). */
    suspend fun startAsKitchen() {
        koin.get<ServerSettingsRepository>().setBaseUrl("$HOST_URL:18080")
        setApiReachable(true)
        login("kitchen.e2e@cassy.local", KITCHEN_PIN)
        // Login carries no permissions; the app loads them from the bootstrap, and the KDS board reads them.
        koin.get<AuthRepository>().fetchMobileBootstrap().getOrThrow()
    }

    /** Access token for a second staff member acting outside the app's own session (the cashier sending orders). */
    fun staffToken(email: String, pin: String): String =
        api("POST", "/auth/staff/login", null, JSONObject().put("store_slug", "e2e-kasir").put("email", email).put("pin", pin))
            .getString("access_token")

    /** JSON call straight to the API through the never-cut proxy. Throws on any non-2xx status. */
    fun api(method: String, path: String, token: String?, body: JSONObject? = null): JSONObject {
        val conn = URL(DIRECT_API + path).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 5_000
        conn.readTimeout = 5_000
        conn.setRequestProperty("Content-Type", "application/json")
        token?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
        return try {
            if (body != null) {
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            check(code in 200..299) { "$method $path -> $code $text" }
            if (text.isBlank()) JSONObject() else JSONObject(text)
        } finally {
            conn.disconnect()
        }
    }

    /** Cuts or restores the app's route to the API (Toxiproxy `cassy_api`); open connections drop when cut. */
    fun setApiReachable(enabled: Boolean) {
        val conn = URL(TOXIPROXY).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        // Toxiproxy refuses browser-like user agents.
        conn.setRequestProperty("User-Agent", "cassy-e2e")
        conn.setRequestProperty("Content-Type", "application/json")
        try {
            conn.outputStream.use { it.write("""{"enabled":$enabled}""".toByteArray()) }
            check(conn.responseCode in 200..299) { "toxiproxy toggle -> ${conn.responseCode}" }
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Points the app at the stack and logs the cashier in on [drawerName]. A shift left open on that
     * drawer by an earlier failed attempt (the CI retry) is force-closed by the manager first.
     */
    suspend fun startOnDrawer(drawerName: String): CashDrawer {
        koin.get<ServerSettingsRepository>().setBaseUrl("$HOST_URL:18080")
        loginManager()
        val shifts = koin.get<ShiftRepository>()
        val drawer = shifts.getDrawers().getOrThrow().first { it.name == drawerName }
        shifts.getCurrentShift(drawer.id).getOrNull()?.let { leftover ->
            shifts.forceCloseShift(leftover.id, reason = "Sisa percobaan uji e2e sebelumnya").getOrThrow()
        }
        loginCashier()
        return drawer
    }

    fun http(method: String, url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 5_000
        conn.readTimeout = 5_000
        return try {
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private suspend fun login(email: String, pin: String) {
        koin.get<AuthRepository>().login(storeSlug = "e2e-kasir", email = email, pin = pin).getOrThrow()
    }
}
