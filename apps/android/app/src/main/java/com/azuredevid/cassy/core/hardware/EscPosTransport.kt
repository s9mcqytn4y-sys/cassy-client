package com.azuredevid.cassy.core.hardware

import androidx.compose.runtime.Immutable

/**
 * Transport endpoint for direct ESC/POS printing from a POS/KDS tablet.
 */
sealed class PrinterEndpoint {
    data class Lan(val host: String, val port: Int = 9100) : PrinterEndpoint() {
        override val displayLabel: String get() = "$host:$port"
    }

    data class Bluetooth(val macAddress: String) : PrinterEndpoint() {
        override val displayLabel: String get() = macAddress
    }

    abstract val displayLabel: String
}

/**
 * Snapshot of a paired Bluetooth Classic device shown in the printer picker.
 */
@Immutable
data class PairedBluetoothDevice(
    val name: String,
    val macAddress: String
)

/**
 * Abstraction for Bluetooth Classic SPP (RFCOMM) thermal printer operations.
 * Isolated behind an interface so ViewModels and routers remain 100% unit-testable without Robolectric.
 */
interface BluetoothPrinterClient {
    fun isBluetoothSupported(): Boolean
    fun isBluetoothEnabled(): Boolean
    fun hasConnectPermission(): Boolean
    fun listBondedDevices(): Result<List<PairedBluetoothDevice>>
    suspend fun checkHealth(macAddress: String): PrinterPort9100Manager.HealthCheckResult
    suspend fun printRawBytes(macAddress: String, payload: ByteArray): Result<Boolean>
    suspend fun openCashDrawer(macAddress: String): Result<Boolean>
}

/**
 * Unified dispatcher routing ESC/POS commands to either LAN TCP Port 9100 or Bluetooth Classic SPP.
 */
class UnifiedEscPosPrinter(
    private val lanManager: PrinterPort9100Manager,
    private val bluetoothClient: BluetoothPrinterClient? = null
) {
    suspend fun checkHealth(endpoint: PrinterEndpoint): PrinterPort9100Manager.HealthCheckResult =
        when (endpoint) {
            is PrinterEndpoint.Lan -> lanManager.checkPrinterHealth(endpoint.host, endpoint.port)
            is PrinterEndpoint.Bluetooth -> bluetoothClient?.checkHealth(endpoint.macAddress)
                ?: PrinterPort9100Manager.HealthCheckResult(
                    PrinterPort9100Manager.PrinterHealth.OFFLINE,
                    "Modul Bluetooth tidak tersedia pada perangkat ini"
                )
        }

    suspend fun printRawBytes(endpoint: PrinterEndpoint, payload: ByteArray): Result<Boolean> =
        when (endpoint) {
            is PrinterEndpoint.Lan -> lanManager.printRawBytes(endpoint.host, payload, endpoint.port)
            is PrinterEndpoint.Bluetooth -> bluetoothClient?.printRawBytes(endpoint.macAddress, payload)
                ?: Result.failure(IllegalStateException("Modul Bluetooth tidak tersedia pada perangkat ini"))
        }

    suspend fun openCashDrawer(endpoint: PrinterEndpoint): Result<Boolean> =
        when (endpoint) {
            is PrinterEndpoint.Lan -> lanManager.openCashDrawer(endpoint.host, endpoint.port)
            is PrinterEndpoint.Bluetooth -> bluetoothClient?.openCashDrawer(endpoint.macAddress)
                ?: Result.failure(IllegalStateException("Modul Bluetooth tidak tersedia pada perangkat ini"))
        }
}
