package com.azuredevid.cassy.core.hardware

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BluetoothEscPosManagerTest {

    @Test
    fun normalizeBluetoothMacValidatesAndUppercases() {
        assertEquals("AA:BB:CC:DD:EE:FF", AndroidBluetoothEscPosManager.normalizeBluetoothMac(" aa:bb:cc:dd:ee:ff "))
        assertNull(AndroidBluetoothEscPosManager.normalizeBluetoothMac("AA-BB-CC-DD-EE-FF"))
        assertNull(AndroidBluetoothEscPosManager.normalizeBluetoothMac("GG:BB:CC:DD:EE:FF"))
        assertNull(AndroidBluetoothEscPosManager.normalizeBluetoothMac("192.168.1.50"))
    }

    @Test
    fun unifiedEscPosPrinterRoutesBluetoothCommandsToClient() = runTest {
        var lastPrintedMac: String? = null
        var drawerOpenedMac: String? = null
        val bt = object : BluetoothPrinterClient {
            override fun isBluetoothSupported() = true
            override fun isBluetoothEnabled() = true
            override fun hasConnectPermission() = true
            override fun listBondedDevices() = Result.success(emptyList<PairedBluetoothDevice>())
            override suspend fun checkHealth(macAddress: String) =
                PrinterPort9100Manager.HealthCheckResult(PrinterPort9100Manager.PrinterHealth.READY)
            override suspend fun printRawBytes(macAddress: String, payload: ByteArray): Result<Boolean> {
                lastPrintedMac = macAddress
                return Result.success(true)
            }
            override suspend fun openCashDrawer(macAddress: String): Result<Boolean> {
                drawerOpenedMac = macAddress
                return Result.success(true)
            }
        }

        val unified = UnifiedEscPosPrinter(PrinterPort9100Manager(), bt)
        val endpoint = PrinterEndpoint.Bluetooth("00:11:22:33:44:55")

        assertEquals(PrinterPort9100Manager.PrinterHealth.READY, unified.checkHealth(endpoint).health)
        assertTrue(unified.printRawBytes(endpoint, byteArrayOf(0x1B, 0x40)).isSuccess)
        assertTrue(unified.openCashDrawer(endpoint).isSuccess)
        assertEquals("00:11:22:33:44:55", lastPrintedMac)
        assertEquals("00:11:22:33:44:55", drawerOpenedMac)
    }
}
