package com.azuredevid.cassy.domain

import com.azuredevid.cassy.domain.model.QrRotationBlock
import com.azuredevid.cassy.domain.model.TableEntry
import com.azuredevid.cassy.domain.model.TableStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TableEntryTest {

    private val free = TableEntry(
        id = "t-1", tableNumber = "1", label = "", section = "", capacity = 4, qrToken = "tok",
        qrRotatedAt = "", isActive = true, status = TableStatus.AVAILABLE, hasOpenSession = false,
        mergedIntoTableId = null, unpaidOrders = 0
    )

    @Test
    fun freeTableMayRotate() {
        assertNull(free.qrRotationBlock())
    }

    @Test
    fun anySessionOrNonAvailableStatusBlocks() {
        assertEquals(QrRotationBlock.ACTIVE_SESSION, free.copy(hasOpenSession = true).qrRotationBlock())
        assertEquals(QrRotationBlock.ACTIVE_SESSION, free.copy(status = TableStatus.OCCUPIED).qrRotationBlock())
        assertEquals(QrRotationBlock.ACTIVE_SESSION, free.copy(status = TableStatus.SETTLED).qrRotationBlock())
    }

    @Test
    fun mergedOrUnpaidBlocks() {
        assertEquals(QrRotationBlock.MERGED, free.copy(mergedIntoTableId = "t-2").qrRotationBlock())
        assertEquals(QrRotationBlock.UNPAID_ORDERS, free.copy(unpaidOrders = 1).qrRotationBlock())
    }

    @Test
    fun unknownWireStatusFailsClosed() {
        assertEquals(TableStatus.OCCUPIED, TableStatus.fromWire("something_new"))
        assertEquals(TableStatus.OCCUPIED, TableStatus.fromWire(null))
        assertEquals(TableStatus.SETTLED, TableStatus.fromWire("settled"))
    }

    @Test
    fun displayNamePrefersLabel() {
        assertEquals("1", free.displayName)
        assertEquals("Sofa", free.copy(label = "Sofa").displayName)
    }
}
