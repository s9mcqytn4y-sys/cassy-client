package com.azuredevid.cassy.domain

import com.azuredevid.cassy.domain.model.KdsAgeLevel
import com.azuredevid.cassy.domain.model.KdsStation
import com.azuredevid.cassy.domain.model.KdsTicket
import com.azuredevid.cassy.domain.model.KdsTicketStatus
import com.azuredevid.cassy.domain.model.KdsTicketStatus.CANCELLED
import com.azuredevid.cassy.domain.model.KdsTicketStatus.PREPARING
import com.azuredevid.cassy.domain.model.KdsTicketStatus.QUEUED
import com.azuredevid.cassy.domain.model.KdsTicketStatus.READY
import com.azuredevid.cassy.domain.model.KdsTicketStatus.SERVED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KdsModelsTest {

    @Test
    fun forwardStepsFollowQueuedPreparingReadyServed() {
        assertEquals(PREPARING, QUEUED.next)
        assertEquals(READY, PREPARING.next)
        assertEquals(SERVED, READY.next)
        assertNull(SERVED.next)
        assertNull(CANCELLED.next)
    }

    @Test
    fun onlyForwardStepsAndReadyRecallAreAllowed() {
        val allowed = KdsTicketStatus.entries.flatMap { from ->
            KdsTicketStatus.entries.filter { to -> from.canMoveTo(to) }.map { to -> from to to }
        }.toSet()
        assertEquals(
            setOf(QUEUED to PREPARING, PREPARING to READY, READY to SERVED, READY to PREPARING),
            allowed
        )
    }

    @Test
    fun onlyQueuedPreparingReadyAreOpen() {
        assertEquals(setOf(QUEUED, PREPARING, READY), KdsTicketStatus.entries.filter { it.isOpen }.toSet())
    }

    @Test
    fun unknownWireStatusIsFinalSoNoBumpIsOffered() {
        assertEquals(CANCELLED, KdsTicketStatus.fromWire("on_fire"))
        assertFalse(KdsTicketStatus.fromWire(null).isOpen)
    }

    @Test
    fun ageBandsUseFiveAndTenMinuteThresholds() {
        assertEquals(KdsAgeLevel.ON_TIME, KdsAgeLevel.of(4))
        assertEquals(KdsAgeLevel.WARNING, KdsAgeLevel.of(5))
        assertEquals(KdsAgeLevel.WARNING, KdsAgeLevel.of(9))
        assertEquals(KdsAgeLevel.LATE, KdsAgeLevel.of(10))
    }

    @Test
    fun ageNeverGoesNegativeWhenTabletClockLags() {
        val ticket = KdsTicket("k", "o", "ORD-1", "A001", "", null, KdsStation.BAR, QUEUED, false, 1_000_000L, emptyList())
        assertEquals(0L, ticket.ageMinutes(nowMillis = 500_000L))
        assertEquals(7L, ticket.ageMinutes(nowMillis = 1_000_000L + 7 * 60_000L + 59_000L))
        assertTrue(KdsStation.fromWire("kitchen") == KdsStation.KITCHEN && KdsStation.fromWire("grill") == null)
    }
}
