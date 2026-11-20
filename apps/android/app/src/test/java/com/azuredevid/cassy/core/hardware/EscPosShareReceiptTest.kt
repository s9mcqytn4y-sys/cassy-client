package com.azuredevid.cassy.core.hardware

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.Charset

class EscPosShareReceiptTest {

    @Test
    fun shareReceiptShowsTheShareNotTheWholeBill() {
        val bytes = EscPosReceiptBuilder(EscPosReceiptBuilder.PaperWidth.WIDTH_80MM).buildShareReceipt(
            storeName = "Kopi Pandan",
            orderId = "ORD-20261120-0007",
            tableName = "Meja 4",
            cashierName = "Rina",
            shareNumber = 1,
            shareCount = 3,
            shareTotalIdr = 37_000L,
            paymentMethod = "TUNAI",
            cashTenderedIdr = 50_000L,
            changeIdr = 13_000L
        )
        val text = String(bytes, Charset.forName("US-ASCII"))

        assertTrue(text.contains("BAGIAN 1 DARI 3"))
        assertTrue(text.contains("37.000") || text.contains("37,000"))
        assertTrue(text.contains("Kembalian"))
        assertTrue(text.contains("struk akhir"))
    }

    @Test
    fun byItemShareListsItsOwnLines() {
        val bytes = EscPosReceiptBuilder(EscPosReceiptBuilder.PaperWidth.WIDTH_80MM).buildShareReceipt(
            storeName = "Kopi Pandan",
            orderId = "ORD-20261120-0008",
            tableName = "Meja 2",
            cashierName = "Rina",
            shareNumber = 2,
            shareCount = 2,
            shareTotalIdr = 6_667L,
            paymentMethod = "QRIS",
            lines = listOf(EscPosReceiptBuilder.ShareReceiptLine("Kopi promo", 2, 6_667L))
        )
        val text = String(bytes, Charset.forName("US-ASCII"))

        assertTrue(text.contains("Kopi promo"))
        assertTrue(text.contains("2x"))
        assertTrue(!text.contains("struk akhir"))
    }
}
