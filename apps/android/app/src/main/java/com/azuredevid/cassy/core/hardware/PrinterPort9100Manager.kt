package com.azuredevid.cassy.core.hardware

import com.azuredevid.cassy.core.coroutines.runCatchingCancellable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Robust LAN Thermal Printer Manager for Port 9100 (ESC/POS).
 * Features:
 * - 2,500 ms strict connection and read timeouts; print and drawer kick retry the connect step only.
 * - tcpNoDelay = true (disables Nagle's algorithm for low-latency printing).
 * - Real-time printer health check via ESC/POS DLE EOT status requests.
 * - Standard cash drawer kick pulse (0x1B, 0x70, 0x00, 0x19, 0xFA).
 */
class PrinterPort9100Manager(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val socketTimeoutMs: Int = 2500,
    private val connectAttempts: Int = 3,
    private val retryDelayMs: Long = 300
) {
    // ESC p m t1 t2 -> ESC/POS drawer kick pulse standard
    private val drawerKickCommand = byteArrayOf(0x1B, 0x70, 0x00, 0x19, 0xFA.toByte())

    enum class PrinterHealth {
        READY,
        PAPER_OUT,
        OFFLINE
    }

    data class HealthCheckResult(
        val health: PrinterHealth,
        val details: String = ""
    )

    /**
     * Probes the printer status using ESC/POS real-time status request (DLE EOT 4).
     */
    suspend fun checkPrinterHealth(printerIp: String, port: Int = 9100): HealthCheckResult = withContext(ioDispatcher) {
        if (printerIp.isBlank()) return@withContext HealthCheckResult(PrinterHealth.OFFLINE, "IP printer kosong")
        try {
            Socket().use { socket ->
                socket.tcpNoDelay = true
                socket.soTimeout = socketTimeoutMs
                socket.connect(InetSocketAddress(printerIp, port), socketTimeoutMs)

                val out = socket.getOutputStream()
                val input = socket.getInputStream()

                // DLE EOT 4 -> Real-time status transmission: Paper sensor status
                out.write(byteArrayOf(0x10, 0x04, 0x04))
                out.flush()

                val responseByte = input.read()
                if (responseByte != -1) {
                    val isPaperEnd = (responseByte and 0x20 != 0) || (responseByte and 0x40 != 0)
                    if (isPaperEnd) {
                        return@withContext HealthCheckResult(PrinterHealth.PAPER_OUT, "Kertas printer habis atau penutup terbuka")
                    }
                }
                HealthCheckResult(PrinterHealth.READY, "Printer online dan siap mencetak")
            }
        } catch (e: Exception) {
            HealthCheckResult(PrinterHealth.OFFLINE, e.message ?: "Printer tidak merespons (timeout)")
        }
    }

    /**
     * Kicks open the cash drawer connected via RJ11 to the thermal receipt printer.
     */
    suspend fun openCashDrawer(printerIp: String, port: Int = 9100): Result<Boolean> = withContext(ioDispatcher) {
        runCatchingCancellable {
            require(printerIp.isNotBlank()) { "IP printer kasir tidak valid" }
            connectWithRetry(printerIp, port).use { socket ->
                val out = socket.getOutputStream()
                out.write(drawerKickCommand)
                out.flush()
                true
            }
        }
    }

    /**
     * Sends raw ESC/POS formatted byte array directly to the printer with strict 2,500ms timeout.
     */
    suspend fun printRawBytes(printerIp: String, payload: ByteArray, port: Int = 9100): Result<Boolean> = withContext(ioDispatcher) {
        runCatchingCancellable {
            require(printerIp.isNotBlank()) { "IP printer tidak boleh kosong" }
            require(payload.isNotEmpty()) { "Payload byte cetak tidak boleh kosong" }

            connectWithRetry(printerIp, port).use { socket ->
                val out = socket.getOutputStream()
                out.write(payload)
                out.flush()
                true
            }
        }
    }

    /**
     * Opens the 9100 socket, retrying only the connect phase: a printer that refuses or times out
     * while another tablet or the print agent holds it gets [connectAttempts] tries with a growing
     * pause. Nothing has been written yet, so a retry can never print a ticket twice.
     */
    private suspend fun connectWithRetry(printerIp: String, port: Int): Socket {
        var lastError: IOException? = null
        repeat(connectAttempts) { attempt ->
            if (attempt > 0) delay(retryDelayMs * attempt)
            val socket = Socket()
            try {
                socket.tcpNoDelay = true
                socket.soTimeout = socketTimeoutMs
                socket.connect(InetSocketAddress(printerIp, port), socketTimeoutMs)
                return socket
            } catch (e: IOException) {
                socket.close()
                lastError = e
            }
        }
        throw lastError ?: IOException("Printer tidak merespons")
    }
}
