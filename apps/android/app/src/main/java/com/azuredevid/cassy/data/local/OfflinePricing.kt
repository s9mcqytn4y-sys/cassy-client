package com.azuredevid.cassy.data.local

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import com.azuredevid.cassy.data.model.OrderQuote
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * What an offline POS needs to quote a cash sale, captured from `GET /api/v1/device/catalog` while
 * online (S4-Q1, ADR-005 OF3). [prices] maps product id to its default variant price; products
 * without an available default variant are left out and cannot be sold offline.
 */
@Serializable
data class OfflinePricingSnapshot(
    val storeSlug: String = "",
    val catalogVersion: String = "",
    /** Starts every offline queue number; the server rejects any other prefix at sync (OF6). */
    val devicePrefix: String = "",
    val taxBps: Long = 0,
    val serviceBps: Long = 0,
    val cashRoundingStep: Long = 0,
    val prices: Map<String, Long> = emptyMap(),
    val fetchedAtMs: Long = 0
) {
    val isUsable: Boolean get() = catalogVersion.isNotBlank() && devicePrefix.isNotBlank()
}

/** An offline quote: same shape as the server's, plus the cash the server will ask for at sync. */
data class OfflineEstimate(val quote: OrderQuote, val cashDue: Long)

/**
 * Mirrors the server's pricing (`ValidateAndPriceOrder` and `roundToNearest`) for default variants
 * without promotions. Not authoritative: the server re-prices at sync and records `price_delta`.
 */
object OfflinePricing {
    /** Marks a quote computed on the device, so receipts can say the official figure follows. */
    const val OFFLINE_FINGERPRINT = "offline"

    /** Null when a line has no offline price (unknown or unavailable product). */
    fun estimate(snapshot: OfflinePricingSnapshot, lines: List<Pair<String, Int>>): OfflineEstimate? {
        if (!snapshot.isUsable || lines.isEmpty()) return null
        var subtotal = 0L
        for ((productId, quantity) in lines) {
            val price = snapshot.prices[productId] ?: return null
            subtotal = Math.addExact(subtotal, Math.multiplyExact(price, quantity.toLong()))
        }
        val service = roundHalfUpBps(subtotal, snapshot.serviceBps)
        val tax = roundHalfUpBps(subtotal + service, snapshot.taxBps)
        val total = subtotal + service + tax
        return OfflineEstimate(
            quote = OrderQuote(
                subtotalAmount = subtotal,
                serviceChargeAmount = service,
                taxAmount = tax,
                totalAmount = total,
                quoteFingerprint = OFFLINE_FINGERPRINT
            ),
            cashDue = roundToNearest(total, snapshot.cashRoundingStep)
        )
    }

    /** floor((amount * bps + 5000) / 10000), as `CalculateRoundHalfUpBPS`. */
    internal fun roundHalfUpBps(amount: Long, bps: Long): Long = (amount * bps + 5_000L) / 10_000L

    /** Nearest step, halves up; step 0 disables rounding, as the server's `roundToNearest`. */
    internal fun roundToNearest(amount: Long, step: Long): Long =
        if (step <= 0) amount else (amount + step / 2) / step * step
}

/** Keeps the latest [OfflinePricingSnapshot] across restarts, since a device may boot already offline. */
class OfflinePricingStore(private val dataStore: DataStore<OfflinePricingSnapshot>) {

    constructor(context: Context, scope: CoroutineScope) : this(
        DataStoreFactory.create(
            serializer = SnapshotSerializer,
            corruptionHandler = ReplaceFileCorruptionHandler { OfflinePricingSnapshot() },
            scope = scope,
            produceFile = { context.applicationContext.dataStoreFile("cassy_offline_pricing.json") }
        )
    )

    suspend fun current(): OfflinePricingSnapshot =
        dataStore.data.catch { e -> if (e is IOException) emit(OfflinePricingSnapshot()) else throw e }.first()

    suspend fun save(snapshot: OfflinePricingSnapshot) {
        dataStore.updateData { snapshot }
    }

    private object SnapshotSerializer : Serializer<OfflinePricingSnapshot> {
        private val json = Json { ignoreUnknownKeys = true }
        override val defaultValue = OfflinePricingSnapshot()
        override suspend fun readFrom(input: InputStream): OfflinePricingSnapshot =
            try {
                json.decodeFromString(OfflinePricingSnapshot.serializer(), input.readBytes().decodeToString())
            } catch (e: SerializationException) {
                throw CorruptionException("Cannot read offline pricing snapshot", e)
            }

        override suspend fun writeTo(t: OfflinePricingSnapshot, output: OutputStream) =
            output.write(json.encodeToString(OfflinePricingSnapshot.serializer(), t).encodeToByteArray())
    }
}
