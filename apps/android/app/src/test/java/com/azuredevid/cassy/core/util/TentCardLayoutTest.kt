package com.azuredevid.cassy.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TentCardLayoutTest {

    private val layout = TentCardLayout()

    @Test
    fun pageIsA6PortraitInPoints() {
        assertEquals(297.6f, layout.pageWidth, 0.1f)
        assertEquals(419.5f, layout.pageHeight, 0.1f)
        assertEquals(layout.pageHeight / 2f, layout.foldY, 0.001f)
    }

    @Test
    fun qrIsCentredAndFitsInsideOneHalfWithMargins() {
        assertEquals(layout.pageWidth - layout.qrLeft - layout.qrSize, layout.qrLeft, 0.001f)
        assertTrue(layout.qrLeft >= layout.margin)
        assertTrue(layout.qrTop + layout.qrSize <= layout.halfHeight - layout.margin)
        assertTrue("QR stays camera-readable", layout.qrSize >= TentCardLayout.MIN_QR)
    }

    @Test
    fun textSitsAroundTheQrWithoutOverlap() {
        assertTrue(layout.storeBaseline < layout.tableBaseline)
        assertTrue(layout.tableBaseline < layout.qrTop)
        assertTrue(layout.hintBaseline > layout.qrTop + layout.qrSize)
        assertTrue(layout.sectionBaseline <= layout.halfHeight - layout.margin)
    }
}
