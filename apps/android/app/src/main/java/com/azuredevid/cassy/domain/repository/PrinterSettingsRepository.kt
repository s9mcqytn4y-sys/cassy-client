package com.azuredevid.cassy.domain.repository

import com.azuredevid.cassy.core.hardware.PrinterEndpoint
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/** Local cache of the store's tablet printers (`GET /api/v1/pos/printers`), so printing works offline. */
@Serializable
data class PrinterConfig(
    val cashierTransportType: String = TRANSPORT_LAN,
    val cashierPrinterIp: String = "",
    val cashierPrinterPort: Int = 9100,
    val cashierBluetoothMac: String = "",
    val barTransportType: String = TRANSPORT_LAN,
    val barPrinterIp: String = "",
    val barPrinterPort: Int = 9100,
    val barBluetoothMac: String = "",
    val kitchenTransportType: String = TRANSPORT_LAN,
    val kitchenPrinterIp: String = "",
    val kitchenPrinterPort: Int = 9100,
    val kitchenBluetoothMac: String = "",
    val expediterTransportType: String = TRANSPORT_LAN,
    val expediterPrinterIp: String = "",
    val expediterPrinterPort: Int = 9100,
    val expediterBluetoothMac: String = "",
    val paperWidthMm: Int = 80,
    /** Tickets per station print (server `copies`, 1-5). */
    val barCopies: Int = 1,
    val kitchenCopies: Int = 1,
    val expediterCopies: Int = 1,
    /** Customer receipts printed per payment (server `copies`, 1-5). */
    val receiptCopies: Int = 1,
    /** Receipt footer set by the owner; blank keeps the default thank-you line. */
    val receiptFooter: String = ""
) {
    val hasCashierPrinter: Boolean
        get() = cashierEndpoint() != null

    fun cashierEndpoint(): PrinterEndpoint? =
        resolveEndpoint(cashierTransportType, cashierPrinterIp, cashierPrinterPort, cashierBluetoothMac)

    fun barEndpoint(): PrinterEndpoint? =
        resolveEndpoint(barTransportType, barPrinterIp, barPrinterPort, barBluetoothMac)

    fun kitchenEndpoint(): PrinterEndpoint? =
        resolveEndpoint(kitchenTransportType, kitchenPrinterIp, kitchenPrinterPort, kitchenBluetoothMac)

    fun expediterEndpoint(): PrinterEndpoint? =
        resolveEndpoint(expediterTransportType, expediterPrinterIp, expediterPrinterPort, expediterBluetoothMac)

    companion object {
        const val TRANSPORT_LAN = "lan"
        const val TRANSPORT_BLUETOOTH = "bluetooth"

        fun resolveEndpoint(
            transportType: String,
            ip: String,
            port: Int,
            bluetoothMac: String
        ): PrinterEndpoint? = when {
            transportType.equals(TRANSPORT_BLUETOOTH, ignoreCase = true) && bluetoothMac.isNotBlank() ->
                PrinterEndpoint.Bluetooth(bluetoothMac.trim().uppercase())
            ip.isNotBlank() ->
                PrinterEndpoint.Lan(ip.trim(), port.takeIf { it in 1..65535 } ?: 9100)
            else -> null
        }
    }
}

interface PrinterSettingsRepository {
    val configFlow: Flow<PrinterConfig>
    suspend fun getConfig(): PrinterConfig
    suspend fun saveConfig(config: PrinterConfig)
    suspend fun clearConfig()
}
