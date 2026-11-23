package com.azuredevid.cassy.live

import com.azuredevid.cassy.data.repository.ServerSettingsRepository
import com.azuredevid.cassy.domain.model.CashDrawer
import com.azuredevid.cassy.domain.repository.AuthRepository
import com.azuredevid.cassy.domain.repository.ShiftRepository
import org.koin.core.context.GlobalContext
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

    private val koin get() = GlobalContext.get()

    suspend fun loginCashier() = login("cashier.e2e@cassy.local", CASHIER_PIN)

    suspend fun loginManager() = login("manager.e2e@cassy.local", MANAGER_PIN)

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
