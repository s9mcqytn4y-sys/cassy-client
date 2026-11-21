package com.azuredevid.cassy.core.hardware

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * EscPosReceiptBuilder generates compliant ESC/POS binary payloads for 58mm (32 chars)
 * and 80mm (48 chars) thermal receipt printers on TCP port 9100.
 */
class EscPosReceiptBuilder(
    val paperWidth: PaperWidth = PaperWidth.WIDTH_80MM,
    private val charset: Charset = Charset.forName("GBK") // or UTF-8 / CP437 based on printer ROM
) {
    enum class PaperWidth(val columns: Int) {
        WIDTH_58MM(32),
        WIDTH_80MM(48)
    }

    private val outputStream = ByteArrayOutputStream()
    private val rupiahFormat = NumberFormat.getIntegerInstance(Locale.forLanguageTag("id-ID"))
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.forLanguageTag("id-ID"))

    // ESC/POS Command Constants
    companion object {
        val ESC_INIT = byteArrayOf(0x1B, 0x40) // ESC @
        val ESC_ALIGN_LEFT = byteArrayOf(0x1B, 0x61, 0x00) // ESC a 0
        val ESC_ALIGN_CENTER = byteArrayOf(0x1B, 0x61, 0x01) // ESC a 1
        val ESC_ALIGN_RIGHT = byteArrayOf(0x1B, 0x61, 0x02) // ESC a 2
        val ESC_BOLD_ON = byteArrayOf(0x1B, 0x45, 0x01) // ESC E 1
        val ESC_BOLD_OFF = byteArrayOf(0x1B, 0x45, 0x00) // ESC E 0
        val GS_TEXT_DOUBLE = byteArrayOf(0x1D, 0x21, 0x11) // GS ! 0x11 (2x width & 2x height)
        val GS_TEXT_NORMAL = byteArrayOf(0x1D, 0x21, 0x00) // GS ! 0x00
        val FEED_AND_CUT = byteArrayOf(0x1B, 0x64, 0x03, 0x1D, 0x56, 0x41, 0x00) // Feed 3 lines & Partial Cut
        val DRAWER_KICK = byteArrayOf(0x1B, 0x70, 0x00, 0x19, 0xFA.toByte()) // RJ11 pulse
    }

    init {
        outputStream.write(ESC_INIT)
    }

    fun kickDrawer(): EscPosReceiptBuilder {
        outputStream.write(DRAWER_KICK)
        return this
    }

    fun alignCenter(): EscPosReceiptBuilder {
        outputStream.write(ESC_ALIGN_CENTER)
        return this
    }

    fun alignLeft(): EscPosReceiptBuilder {
        outputStream.write(ESC_ALIGN_LEFT)
        return this
    }

    fun alignRight(): EscPosReceiptBuilder {
        outputStream.write(ESC_ALIGN_RIGHT)
        return this
    }

    fun bold(enable: Boolean): EscPosReceiptBuilder {
        outputStream.write(if (enable) ESC_BOLD_ON else ESC_BOLD_OFF)
        return this
    }

    fun title(text: String): EscPosReceiptBuilder {
        alignCenter()
        outputStream.write(GS_TEXT_DOUBLE)
        outputStream.write(ESC_BOLD_ON)
        writeLine(text)
        outputStream.write(GS_TEXT_NORMAL)
        outputStream.write(ESC_BOLD_OFF)
        return this
    }

    fun text(text: String): EscPosReceiptBuilder {
        writeLine(text)
        return this
    }

    fun separator(char: Char = '-'): EscPosReceiptBuilder {
        alignLeft()
        val line = char.toString().repeat(paperWidth.columns)
        writeLine(line)
        return this
    }

    fun doubleSeparator(): EscPosReceiptBuilder {
        return separator('=')
    }

    /**
     * Formats two-column text with left and right alignment.
     * Truncates or wraps left column if sum of lengths exceeds total columns.
     */
    fun row(left: String, right: String): EscPosReceiptBuilder {
        alignLeft()
        val maxCols = paperWidth.columns
        val rightLen = right.length
        val maxLeftLen = maxCols - rightLen - 1

        val leftTrimmed = if (left.length > maxLeftLen) {
            left.substring(0, maxLeftLen.coerceAtLeast(0))
        } else {
            left
        }

        val spacesCount = (maxCols - leftTrimmed.length - rightLen).coerceAtLeast(1)
        val formatted = leftTrimmed + " ".repeat(spacesCount) + right
        writeLine(formatted)
        return this
    }

    /**
     * Prints an itemized row: "Product Name", "Qty x Price", "Total Price"
     */
    fun item(name: String, qty: Int, priceIdr: Long): EscPosReceiptBuilder {
        alignLeft()
        val totalIdr = qty * priceIdr
        val totalStr = "Rp " + rupiahFormat.format(totalIdr)
        
        // Print Product Name on first line
        writeLine(name)
        
        // Print "Qty x Price" on left, "Total" on right
        val detailLeft = "  ${qty}x @ Rp ${rupiahFormat.format(priceIdr)}"
        row(detailLeft, totalStr)
        return this
    }

    /**
     * Prints a QR code with the printer's native QR engine (`GS ( k`, model 2), so the code stays sharp
     * at any [moduleDots] size instead of being rasterised on the tablet. [data] must be ASCII (a URL);
     * error correction is level M, which survives light scuffing on a table sticker.
     *
     * @param moduleDots dot width of one QR module, 1-16. 8 gives about 41 mm for a 70-character URL
     *   on a 203 dpi 80 mm head.
     */
    fun qrCode(data: String, moduleDots: Int = 8): EscPosReceiptBuilder {
        require(data.isNotEmpty()) { "QR data must not be empty" }
        require(data.all { it.code in 0x20..0x7E }) { "QR data must be printable ASCII" }
        require(moduleDots in 1..16) { "moduleDots must be 1-16" }
        val bytes = data.toByteArray(Charsets.US_ASCII)
        val storeLength = bytes.size + 3
        require(storeLength <= 0xFFFF) { "QR data too long" }
        // Model 2.
        outputStream.write(byteArrayOf(0x1D, 0x28, 0x6B, 0x04, 0x00, 0x31, 0x41, 0x32, 0x00))
        // Module size.
        outputStream.write(byteArrayOf(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x43, moduleDots.toByte()))
        // Error correction level M (0x31).
        outputStream.write(byteArrayOf(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x45, 0x31))
        // Store data: pL pH = length of data + 3.
        outputStream.write(
            byteArrayOf(0x1D, 0x28, 0x6B, (storeLength and 0xFF).toByte(), (storeLength shr 8).toByte(), 0x31, 0x50, 0x30)
        )
        outputStream.write(bytes)
        // Print the stored symbol.
        outputStream.write(byteArrayOf(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x51, 0x30))
        outputStream.write("\n".toByteArray(charset))
        return this
    }

    fun emptyLine(lines: Int = 1): EscPosReceiptBuilder {
        repeat(lines) {
            outputStream.write("\n".toByteArray(charset))
        }
        return this
    }

    fun feedAndCut(): EscPosReceiptBuilder {
        outputStream.write(FEED_AND_CUT)
        return this
    }

    private fun writeLine(text: String) {
        outputStream.write(text.toByteArray(charset))
        outputStream.write("\n".toByteArray(charset))
    }

    fun build(): ByteArray {
        return outputStream.toByteArray()
    }

    /**
     * Helper to build a complete customer receipt for Cassy POS.
     */
    fun buildStoreReceipt(
        storeName: String,
        storeAddress: String,
        orderId: String,
        tableName: String,
        cashierName: String,
        items: List<ReceiptItem>,
        subtotalIdr: Long,
        pb1TaxIdr: Long,
        grandTotalIdr: Long,
        paymentMethod: String,
        cashTenderedIdr: Long? = null,
        changeIdr: Long? = null,
        footerNotes: String = DEFAULT_RECEIPT_FOOTER,
        serviceChargeIdr: Long = 0L,
        discountIdr: Long = 0L,
        roundingIdr: Long = 0L,
        taxLabel: String = "Pajak:"
    ): ByteArray {
        title(storeName)
        if (storeAddress.isNotBlank()) {
            alignCenter()
            text(storeAddress)
        }
        separator()
        
        alignLeft()
        row("No. Pesanan:", orderId.takeLast(12).uppercase())
        row("Meja / Tipe:", tableName)
        row("Kasir:", cashierName)
        row("Waktu:", dateFormat.format(Date()))
        doubleSeparator()

        items.forEach { item ->
            item(item.name, item.qty, item.priceIdr)
        }

        separator()
        row("Subtotal:", "Rp " + rupiahFormat.format(subtotalIdr))
        if (discountIdr > 0L) {
            row("Diskon:", "-Rp " + rupiahFormat.format(discountIdr))
        }
        if (serviceChargeIdr > 0L) {
            row("Layanan:", "Rp " + rupiahFormat.format(serviceChargeIdr))
        }
        if (pb1TaxIdr > 0L) {
            row(taxLabel, "Rp " + rupiahFormat.format(pb1TaxIdr))
        }
        if (roundingIdr != 0L) {
            val prefix = if (roundingIdr > 0L) "+Rp " else "-Rp "
            row("Pembulatan:", prefix + rupiahFormat.format(kotlin.math.abs(roundingIdr)))
        }
        bold(true)
        row("TOTAL TAGIHAN:", "Rp " + rupiahFormat.format(grandTotalIdr))
        bold(false)
        separator()

        row("Metode Bayar:", paymentMethod)
        if (cashTenderedIdr != null && cashTenderedIdr > 0) {
            row("Tunai Diterima:", "Rp " + rupiahFormat.format(cashTenderedIdr))
            row("Kembalian:", "Rp " + rupiahFormat.format(changeIdr ?: 0))
        }
        separator()

        if (footerNotes.isNotBlank()) {
            alignCenter()
            text(footerNotes)
        }
        emptyLine(1)
        feedAndCut()

        return build()
    }

    /**
     * Receipt for one share of a split bill: the share amount and how it was paid. Line items stay on
     * the full receipt that prints when the last share is paid.
     */
    fun buildShareReceipt(
        storeName: String,
        orderId: String,
        tableName: String,
        cashierName: String,
        shareNumber: Int,
        shareCount: Int,
        shareTotalIdr: Long,
        paymentMethod: String,
        lines: List<ShareReceiptLine> = emptyList(),
        cashTenderedIdr: Long? = null,
        changeIdr: Long? = null
    ): ByteArray {
        title(storeName)
        separator()
        alignLeft()
        row("No. Pesanan:", orderId.takeLast(12).uppercase())
        row("Meja / Tipe:", tableName)
        row("Kasir:", cashierName)
        row("Waktu:", dateFormat.format(Date()))
        doubleSeparator()
        lines.forEach { line ->
            writeLine(line.name)
            row("  ${line.qty}x", "Rp " + rupiahFormat.format(line.amountIdr))
        }
        if (lines.isNotEmpty()) separator()
        bold(true)
        row("BAGIAN $shareNumber DARI $shareCount:", "Rp " + rupiahFormat.format(shareTotalIdr))
        bold(false)
        separator()
        row("Metode Bayar:", paymentMethod)
        if (cashTenderedIdr != null && cashTenderedIdr > 0) {
            row("Tunai Diterima:", "Rp " + rupiahFormat.format(cashTenderedIdr))
            row("Kembalian:", "Rp " + rupiahFormat.format(changeIdr ?: 0))
        }
        separator()
        alignCenter()
        if (lines.isEmpty()) text("Rincian pesanan ada di struk akhir.")
        emptyLine(1)
        feedAndCut()
        return build()
    }

    /** A share's slice of one order line; [amountIdr] is the server allocation, not qty times price. */
    data class ShareReceiptLine(val name: String, val qty: Int, val amountIdr: Long)

    data class ReceiptItem(
        val name: String,
        val qty: Int,
        val priceIdr: Long
    )
}

/** Receipt footer used when the owner has not set one on the receipt printer. */
const val DEFAULT_RECEIPT_FOOTER = "Terima kasih atas kunjungan Anda!"

/** Each slip ends with its own cut, so back-to-back copies print as separate slips. */
fun ByteArray.repeatCopies(copies: Int): ByteArray {
    val n = copies.coerceIn(1, 5)
    return if (n == 1) this else ByteArray(size * n) { this[it % size] }
}
