package com.azuredevid.cassy.data.network

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RealtimeClientLogicTest {

    @Test
    fun sequenceTracker_dropsRepeatsAndReplayOverlap() {
        val tracker = RealtimeSequenceTracker()
        assertEquals(-1L, tracker.lastSeq)

        val delivered = listOf(10L, 11L, 11L, 9L, 12L, 12L, 13L).filter { tracker.accept(it) }

        assertEquals(listOf(10L, 11L, 12L, 13L), delivered)
        assertEquals(13L, tracker.lastSeq)
    }

    @Test
    fun sequenceTracker_alwaysPassesUnsequencedEvents() {
        val tracker = RealtimeSequenceTracker()
        tracker.accept(50L)
        assertTrue("resync_required carries no seq", tracker.accept(0L))
        assertTrue(tracker.accept(0L))
        assertEquals(50L, tracker.lastSeq)
    }

    @Test
    fun backoff_ceilingGrowsExponentiallyAndCaps() {
        val backoff = ReconnectBackoff(baseMillis = 500L, capMillis = 30_000L)
        assertEquals(listOf(500L, 1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L), (0..7).map(backoff::ceilingMillis))
        assertEquals("huge attempts stay capped", 30_000L, backoff.ceilingMillis(10_000))
    }

    @Test
    fun backoff_fullJitterStaysWithinZeroAndCeiling() {
        val backoff = ReconnectBackoff(baseMillis = 500L, capMillis = 30_000L, random = Random(42))
        for (attempt in 0..12) {
            repeat(200) {
                val delay = backoff.delayMillis(attempt)
                assertTrue("attempt $attempt delay $delay", delay in 0L..backoff.ceilingMillis(attempt))
            }
        }
    }

    @Test
    fun backoff_jitterSpreadsTabletsThatDroppedTogether() {
        val delays = (0 until 20).map { ReconnectBackoff(random = Random(it)).delayMillis(attempt = 6) }
        assertTrue("delays must not reconnect in lockstep: $delays", delays.toSet().size >= 15)
        assertTrue("full jitter reaches the low half", delays.any { it < 15_000L })
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun envelope_parsesServerShapeWithObjectPayload() {
        val frame = """{"seq":42,"type":"kds.ticket_updated","store_id":"s-1","payload":{"ticket_id":"k-1","station":"bar","status":"ready"}}"""

        val envelope = json.decodeFromString(RealtimeEventEnvelope.serializer(), frame)

        assertEquals(42L, envelope.seq)
        assertEquals("kds.ticket_updated", envelope.type)
        assertEquals("s-1", envelope.storeId)
        assertEquals("bar", envelope.payloadString("station"))
        assertNull(envelope.payloadString("missing"))
        assertTrue(envelope.payloadJson.contains("\"ticket_id\":\"k-1\""))
    }

    @Test
    fun envelope_parsesResyncWithoutSeq() {
        val envelope = json.decodeFromString(
            RealtimeEventEnvelope.serializer(),
            """{"type":"resync_required","store_id":"s-1","payload":{"last_seq":3}}"""
        )
        assertEquals(0L, envelope.seq)
        assertEquals(RealtimeEventEnvelope.TYPE_RESYNC_REQUIRED, envelope.type)
        assertFalse(envelope.payloadJson.isEmpty())
    }
}
