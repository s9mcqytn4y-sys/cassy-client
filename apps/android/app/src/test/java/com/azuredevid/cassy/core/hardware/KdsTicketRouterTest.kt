package com.azuredevid.cassy.core.hardware

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket
import kotlin.concurrent.thread

class KdsTicketRouterTest {

    @Test
    fun receiptGoesToTheReceiptPrinterPortAndNoStationPrintsWithoutItems() {
        ServerSocket(0).use { printer ->
            var received = ByteArray(0)
            val reader = thread { printer.accept().use { received = it.getInputStream().readBytes() } }
            val receipt = "STRUK".toByteArray()

            val result = runBlocking {
                KdsTicketRouter(PrinterPort9100Manager(Dispatchers.IO)).routeOrderDynamic(
                    orderNumber = "TAB-001",
                    tableOrQueue = "07",
                    // A paid tab whose rounds were all printed when sent: nothing left for the kitchen.
                    items = emptyList(),
                    stations = listOf(KdsTicketRouter.StationPrinterConfig("kitchen", "DAPUR", "127.0.0.1", null, 1)),
                    cashierPrinterIp = "127.0.0.1",
                    cashierPrinterPort = printer.localPort,
                    cashierReceiptBytes = receipt
                )
            }
            reader.join(5_000)

            assertTrue(result.cashierReceiptPrinted)
            assertTrue(result.stationStatuses.isEmpty())
            assertArrayEquals(receipt, received)
        }
    }
}
