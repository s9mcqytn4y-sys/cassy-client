package com.azuredevid.cassy.live

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL

/**
 * S0 live smoke against the Docker test stack (cassy-docker `Core/DockerComposeTest.yml`).
 *
 * The emulator reaches host ports through 10.0.2.2: API via Toxiproxy on 18080,
 * virtual ESC/POS printer on 9100 and its job API on 9101. Run only by
 * `android-live-e2e.yml`; the screenshot workflow filters by class and never picks it up.
 */
@RunWith(AndroidJUnit4::class)
class StackSmokeTest {

    @Test
    fun apiIsHealthyThroughToxiproxy() {
        val (code, body) = get("$HOST_URL:18080/api/v1/health")
        assertEquals(200, code)
        assertTrue(body, body.contains("\"database\":\"connected\""))
    }

    @Test
    fun printedBytesReachVirtualPrinter() {
        val marker = "CASSY-S0-${System.currentTimeMillis()}"
        Socket().use { socket ->
            socket.connect(InetSocketAddress(HOST, 9100), TIMEOUT_MS)
            socket.getOutputStream().apply {
                write(byteArrayOf(0x1B, 0x40)) // ESC @ init
                write("$marker\n".toByteArray(Charsets.US_ASCII))
                flush()
            }
        }
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        var jobs = ""
        while (System.currentTimeMillis() < deadline && !jobs.contains(marker)) {
            Thread.sleep(200)
            jobs = get("$HOST_URL:9101/jobs").second
        }
        assertTrue("printer job not captured: $jobs", jobs.contains(marker))
    }

    private fun get(url: String): Pair<Int, String> {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = TIMEOUT_MS
        conn.readTimeout = TIMEOUT_MS
        return try {
            conn.responseCode to conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private companion object {
        const val HOST = "10.0.2.2"
        const val HOST_URL = "http://$HOST"
        const val TIMEOUT_MS = 5_000
    }
}
