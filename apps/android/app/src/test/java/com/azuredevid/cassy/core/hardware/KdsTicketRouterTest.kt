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

    @Test
    fun offlineLanKitchenFailsOverToBluetoothCashierPrinter() {
        val printedToBt = mutableListOf<Pair<String, ByteArray>>()
        val fakeBt = object : BluetoothPrinterClient {
            override fun isBluetoothSupported() = true
            override fun isBluetoothEnabled() = true
            override fun hasConnectPermission() = true
            override fun listBondedDevices() = Result.success(emptyList<PairedBluetoothDevice>())
            override suspend fun checkHealth(macAddress: String) =
                PrinterPort9100Manager.HealthCheckResult(PrinterPort9100Manager.PrinterHealth.READY)
            override suspend fun printRawBytes(macAddress: String, payload: ByteArray): Result<Boolean> {
                printedToBt += macAddress to payload
                return Result.success(true)
            }
            override suspend fun openCashDrawer(macAddress: String) = Result.success(true)
        }

        val result = runBlocking {
            KdsTicketRouter(
                printerManager = PrinterPort9100Manager(Dispatchers.IO, socketTimeoutMs = 200),
                ioDispatcher = Dispatchers.IO,
                bluetoothPrinter = fakeBt
            ).routeOrderDynamic(
                orderNumber = "ORD-BT-1",
                tableOrQueue = "Meja 3",
                items = listOf(KdsTicketRouter.StationItem("Nasi Goreng", 1, "kitchen")),
                stations = listOf(
                    KdsTicketRouter.StationPrinterConfig(
                        stationId = "kitchen",
                        displayName = "DAPUR",
                        primaryIp = "127.0.0.1",
                        port = 1,
                        fallbackEndpoint = PrinterEndpoint.Bluetooth("AA:BB:CC:DD:EE:FF")
                    )
                ),
                cashierTransportType = "bluetooth",
                cashierBluetoothMac = "AA:BB:CC:DD:EE:FF",
                cashierReceiptBytes = "STRUK-BT".toByteArray()
            )
        }

        assertTrue(result.hasAnyFailover)
        assertTrue(result.cashierReceiptPrinted)
        assertTrue(printedToBt.size == 2)
        assertTrue(printedToBt.all { it.first == "AA:BB:CC:DD:EE:FF" })
    }
}
