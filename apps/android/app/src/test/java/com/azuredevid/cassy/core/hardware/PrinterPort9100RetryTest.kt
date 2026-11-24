package com.azuredevid.cassy.core.hardware

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket

class PrinterPort9100RetryTest {

    private fun freePort(): Int = ServerSocket(0).use { it.localPort }

    /** A printer busy with another tablet refuses the first connect; the job must still print once. */
    @Test
    fun printRawBytes_retriesConnectUntilPrinterAccepts() = runBlocking(Dispatchers.IO) {
        val port = freePort()
        val payload = byteArrayOf(0x1B, 0x40, 0x41)
        val received = async {
            delay(150)
            ServerSocket(port).use { server -> server.accept().use { it.getInputStream().readBytes() } }
        }

        val result = PrinterPort9100Manager(Dispatchers.IO, socketTimeoutMs = 500, retryDelayMs = 300)
            .printRawBytes("127.0.0.1", payload, port)

        assertTrue(result.isSuccess)
        assertArrayEquals(payload, received.await())
    }

    @Test
    fun printRawBytes_failsAfterAllConnectAttempts() = runBlocking(Dispatchers.IO) {
        val result = PrinterPort9100Manager(Dispatchers.IO, socketTimeoutMs = 200, retryDelayMs = 10)
            .printRawBytes("127.0.0.1", byteArrayOf(0x41), freePort())

        assertTrue(result.isFailure)
    }
}
