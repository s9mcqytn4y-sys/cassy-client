package com.azuredevid.cassy.core.hardware

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TableQrPosterBuilderTest {

    private val url = "https://demo.cassy.test/order/AbC_123-xyz"
    private val copy = TableQrPosterCopy(heading = "Meja", scanHint = "Pindai untuk lihat menu dan pesan", footer = "Bayar dari ponsel")

    private fun ByteArray.indexOf(pattern: ByteArray): Int {
        outer@ for (i in 0..size - pattern.size) {
            for (j in pattern.indices) if (this[i + j] != pattern[j]) continue@outer
            return i
        }
        return -1
    }

    @Test
    fun qrCode_emitsNativeModel2SequenceWithLengthPrefix() {
        val bytes = EscPosReceiptBuilder().qrCode(url, moduleDots = 8).build()
        val data = url.toByteArray(Charsets.US_ASCII)
        val storeLength = data.size + 3

        val expected = byteArrayOf(0x1D, 0x28, 0x6B, 0x04, 0x00, 0x31, 0x41, 0x32, 0x00) +
            byteArrayOf(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x43, 0x08) +
            byteArrayOf(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x45, 0x31) +
            byteArrayOf(0x1D, 0x28, 0x6B, storeLength.toByte(), 0x00, 0x31, 0x50, 0x30) + data +
            byteArrayOf(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x51, 0x30)

        // ESC @ comes first, then the QR block.
        assertArrayEquals(byteArrayOf(0x1B, 0x40), bytes.copyOfRange(0, 2))
        assertArrayEquals(expected, bytes.copyOfRange(2, 2 + expected.size))
    }

    @Test(expected = IllegalArgumentException::class)
    fun qrCode_rejectsNonAscii() {
        EscPosReceiptBuilder().qrCode("https://kafe.test/order/é")
    }

    @Test(expected = IllegalArgumentException::class)
    fun qrCode_rejectsModuleSizeOutOfRange() {
        EscPosReceiptBuilder().qrCode(url, moduleDots = 17)
    }

    @Test
    fun poster_containsTableNameQrHintAndEndsWithFeedAndCut() {
        val bytes = TableQrPosterBuilder().build(
            storeName = "Kopi Nusantara",
            tableName = "Sofa Teras",
            section = "Lantai 2",
            qrUrl = url,
            copy = copy
        )
        val text = String(bytes, Charsets.ISO_8859_1)

        assertTrue(text.contains("Kopi Nusantara"))
        assertTrue(text.contains("Sofa Teras"))
        assertTrue(text.contains("Lantai 2"))
        assertTrue(text.contains(copy.scanHint))
        assertTrue("QR data present", bytes.indexOf(url.toByteArray()) > 0)
        assertTrue("table name precedes QR", text.indexOf("Sofa Teras") < text.indexOf(url))
        assertArrayEquals(EscPosReceiptBuilder.FEED_AND_CUT, bytes.copyOfRange(bytes.size - EscPosReceiptBuilder.FEED_AND_CUT.size, bytes.size))
        // Module size 8 for 80 mm paper.
        assertTrue(bytes.indexOf(byteArrayOf(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x43, 0x08)) > 0)
    }

    @Test
    fun poster_on58mmUsesSmallerModules() {
        val bytes = TableQrPosterBuilder(EscPosReceiptBuilder.PaperWidth.WIDTH_58MM)
            .build("", "1", "", url, copy)
        assertTrue(bytes.indexOf(byteArrayOf(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x43, 0x05)) > 0)
        assertEquals(-1, String(bytes, Charsets.ISO_8859_1).indexOf("Kopi"))
    }
}
