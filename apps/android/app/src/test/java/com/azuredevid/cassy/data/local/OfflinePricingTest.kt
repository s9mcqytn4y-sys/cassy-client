package com.azuredevid.cassy.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OfflinePricingTest {

    private val snapshot = OfflinePricingSnapshot(
        storeSlug = "e2e-kasir",
        catalogVersion = "v1",
        taxBps = 1000,
        serviceBps = 500,
        cashRoundingStep = 500,
        prices = mapOf("kopi" to 25_000L, "nasi" to 32_000L)
    )

    /** Same figures the server quotes for the e2e-kasir store (S2 live receipts: Kopi Uji = Rp 28.875). */
    @Test
    fun matchesServerPricingAndRoundsTheCashDue() {
        val one = OfflinePricing.estimate(snapshot, listOf("kopi" to 1))!!
        assertEquals(25_000L, one.quote.subtotalAmount)
        assertEquals(1_250L, one.quote.serviceChargeAmount)
        assertEquals(2_625L, one.quote.taxAmount)
        assertEquals(28_875L, one.quote.totalAmount)
        assertEquals(29_000L, one.cashDue)
        assertEquals(OfflinePricing.OFFLINE_FINGERPRINT, one.quote.quoteFingerprint)

        val mixed = OfflinePricing.estimate(snapshot, listOf("kopi" to 2, "nasi" to 1))!!
        assertEquals(82_000L, mixed.quote.subtotalAmount)
        assertEquals(4_100L, mixed.quote.serviceChargeAmount)
        assertEquals(8_610L, mixed.quote.taxAmount)
        assertEquals(94_710L, mixed.quote.totalAmount)
        assertEquals(94_500L, mixed.cashDue)
    }

    @Test
    fun halvesRoundUpLikeTheServer() {
        assertEquals(1L, OfflinePricing.roundHalfUpBps(5, 1000))
        assertEquals(0L, OfflinePricing.roundHalfUpBps(4, 1000))
        assertEquals(500L, OfflinePricing.roundToNearest(250, 500))
        assertEquals(0L, OfflinePricing.roundToNearest(249, 500))
        assertEquals(28_875L, OfflinePricing.roundToNearest(28_875, 0))
    }

    @Test
    fun refusesWhatItCannotPrice() {
        assertNull("unknown product", OfflinePricing.estimate(snapshot, listOf("teh" to 1)))
        assertNull("no snapshot yet", OfflinePricing.estimate(OfflinePricingSnapshot(), listOf("kopi" to 1)))
        assertNull("empty cart", OfflinePricing.estimate(snapshot, emptyList()))
    }
}
