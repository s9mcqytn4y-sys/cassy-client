package com.azuredevid.cassy.domain.repository

/**
 * A printer registered on the server. With [lanAddress] ("host:port") the tablets print to it
 * directly; without one the outlet's print agent prints it from the server queue.
 */
data class StorePrinter(
    val id: String,
    val name: String,
    val station: String,
    val lanAddress: String? = null,
    val paperWidthMm: Int = 58,
    val isActive: Boolean = true,
    val copies: Int = 1,
    val footer: String = ""
) {
    val host: String get() = lanAddress?.substringBeforeLast(':').orEmpty()
    val port: Int get() = lanAddress?.substringAfterLast(':', "")?.toIntOrNull() ?: DEFAULT_PORT

    companion object {
        const val DEFAULT_PORT = 9100
        /** Stations a tablet prints: the cashier receipt, the two prep stations and the checker. */
        val TABLET_STATIONS = listOf("receipt", "bar", "kitchen", "checker")
    }
}

/** Create or change a printer. An empty [lanAddress] hands the printer back to the print agent. */
data class StorePrinterInput(
    val name: String,
    val station: String,
    val lanAddress: String,
    val paperWidthMm: Int
)

interface StorePrinterRepository {
    /** Active printers with a LAN address; readable by any staff, so every tablet can sync them. */
    suspend fun listForTablet(): Result<List<StorePrinter>>

    /** Every printer of the store (owner or manager). */
    suspend fun listAll(): Result<List<StorePrinter>>

    suspend fun create(input: StorePrinterInput): Result<StorePrinter>

    suspend fun update(id: String, input: StorePrinterInput): Result<StorePrinter>
}

/**
 * The local print cache built from the server list: the first active printer with an address per
 * station wins, and the receipt printer sets the receipt paper width.
 */
fun List<StorePrinter>.toPrinterConfig(): PrinterConfig {
    val byStation = filter { it.isActive && it.lanAddress != null }.groupBy { it.station }.mapValues { it.value.first() }
    val receipt = byStation["receipt"]
    val bar = byStation["bar"]
    val kitchen = byStation["kitchen"]
    val checker = byStation["checker"]
    return PrinterConfig(
        cashierPrinterIp = receipt?.host.orEmpty(),
        cashierPrinterPort = receipt?.port ?: StorePrinter.DEFAULT_PORT,
        barPrinterIp = bar?.host.orEmpty(),
        barPrinterPort = bar?.port ?: StorePrinter.DEFAULT_PORT,
        kitchenPrinterIp = kitchen?.host.orEmpty(),
        kitchenPrinterPort = kitchen?.port ?: StorePrinter.DEFAULT_PORT,
        expediterPrinterIp = checker?.host.orEmpty(),
        expediterPrinterPort = checker?.port ?: StorePrinter.DEFAULT_PORT,
        paperWidthMm = receipt?.paperWidthMm ?: 80,
        receiptCopies = (receipt?.copies ?: 1).coerceIn(1, 5),
        barCopies = (bar?.copies ?: 1).coerceIn(1, 5),
        kitchenCopies = (kitchen?.copies ?: 1).coerceIn(1, 5),
        expediterCopies = (checker?.copies ?: 1).coerceIn(1, 5),
        receiptFooter = receipt?.footer.orEmpty()
    )
}
