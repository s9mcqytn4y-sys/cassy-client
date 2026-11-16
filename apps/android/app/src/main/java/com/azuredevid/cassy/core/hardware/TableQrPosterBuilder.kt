package com.azuredevid.cassy.core.hardware

/**
 * Printed text for a table QR poster, resolved from string resources by the UI so the poster follows
 * the app language. Nothing here is computed on the device.
 */
data class TableQrPosterCopy(
    val heading: String,
    val scanHint: String,
    val footer: String
)

/**
 * Builds an 80 mm ESC/POS poster for one table: store name, table name, section, the guest ordering QR
 * (native printer QR, see [EscPosReceiptBuilder.qrCode]), a scan hint, and a footer, then feed and cut.
 * Output goes to `PrinterPort9100Manager.printRawBytes` on port 9100.
 */
class TableQrPosterBuilder(
    private val paperWidth: EscPosReceiptBuilder.PaperWidth = EscPosReceiptBuilder.PaperWidth.WIDTH_80MM
) {
    /**
     * @param qrUrl the URL from `TableQrUrl.build`; must be non-blank ASCII.
     * @throws IllegalArgumentException when [qrUrl] cannot be encoded by the printer.
     */
    fun build(
        storeName: String,
        tableName: String,
        section: String,
        qrUrl: String,
        copy: TableQrPosterCopy
    ): ByteArray {
        val moduleDots = if (paperWidth == EscPosReceiptBuilder.PaperWidth.WIDTH_80MM) 8 else 5
        val builder = EscPosReceiptBuilder(paperWidth)
        builder.alignCenter()
        if (storeName.isNotBlank()) {
            builder.bold(true).text(storeName).bold(false)
        }
        builder.text(copy.heading)
        builder.title(tableName)
        if (section.isNotBlank()) {
            builder.alignCenter().text(section)
        }
        builder.emptyLine()
        builder.alignCenter().qrCode(qrUrl, moduleDots)
        builder.emptyLine()
        builder.bold(true).text(copy.scanHint).bold(false)
        if (copy.footer.isNotBlank()) {
            builder.text(copy.footer)
        }
        builder.emptyLine(2)
        builder.feedAndCut()
        return builder.build()
    }
}
