package com.azuredevid.cassy.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/** Local cache of the store's tablet printers (`GET /api/v1/pos/printers`), so printing works offline. */
@Serializable
data class PrinterConfig(
    val cashierPrinterIp: String = "",
    val cashierPrinterPort: Int = 9100,
    val barPrinterIp: String = "",
    val barPrinterPort: Int = 9100,
    val kitchenPrinterIp: String = "",
    val kitchenPrinterPort: Int = 9100,
    val expediterPrinterIp: String = "",
    val expediterPrinterPort: Int = 9100,
    val paperWidthMm: Int = 80
) {
    val hasCashierPrinter: Boolean
        get() = cashierPrinterIp.isNotBlank()
}

interface PrinterSettingsRepository {
    val configFlow: Flow<PrinterConfig>
    suspend fun getConfig(): PrinterConfig
    suspend fun saveConfig(config: PrinterConfig)
    suspend fun clearConfig()
}
