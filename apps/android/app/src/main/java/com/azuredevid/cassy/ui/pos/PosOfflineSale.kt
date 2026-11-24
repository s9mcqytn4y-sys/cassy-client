package com.azuredevid.cassy.ui.pos

import com.azuredevid.cassy.R
import com.azuredevid.cassy.core.security.SessionManager
import com.azuredevid.cassy.data.local.OfflineLimitReachedException
import com.azuredevid.cassy.data.local.OfflineOrderItemPayload
import com.azuredevid.cassy.data.local.OfflinePricing
import com.azuredevid.cassy.data.local.OfflinePricingStore
import com.azuredevid.cassy.data.local.OfflineSyncCoordinator
import com.azuredevid.cassy.domain.repository.PrinterSettingsRepository
import com.azuredevid.cassy.domain.model.PosOrder
import com.azuredevid.cassy.ui.designsystem.UiText
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Cash sale while the server is unreachable (S4-Q1, S4-Q2, ADR-005 OF1). The total is the device's
 * estimate from the last device catalog; the order joins the Room queue and the server re-prices it
 * at sync. Tabs, tables and anything but cash stay online-only.
 */
internal class PosOfflineSale(
    private val sync: OfflineSyncCoordinator,
    private val pricing: OfflinePricingStore,
    private val sessionManager: SessionManager,
    private val printerSettings: PrinterSettingsRepository,
    private val printDispatcher: PosPrintDispatcher,
    private val clock: () -> LocalTime = LocalTime::now
) {
    sealed interface Outcome {
        data class Queued(val number: String, val changeIdr: Long) : Outcome
        data class Refused(val reason: UiText) : Outcome
    }

    /** Device quote for [cart], or null when a line has no offline price. */
    suspend fun quote(cart: List<CartItem>) = OfflinePricing.estimate(pricing.current(), cart.map { it.product.id to it.quantity })

    suspend fun sell(snapshot: PosUiState, tenderedIdr: Long, shiftId: String?): Outcome {
        if (snapshot.tableId != null) return refused(R.string.pos_offline_no_tabs)
        val staff = sessionManager.currentSession()?.staff ?: return refused(R.string.pos_offline_no_session)
        if (shiftId.isNullOrBlank()) return refused(R.string.pos_offline_no_shift)
        val catalog = pricing.current()
        val estimate = OfflinePricing.estimate(catalog, snapshot.cart.map { it.product.id to it.quantity })
            ?: return refused(R.string.pos_offline_unpriced)
        if (tenderedIdr < estimate.cashDue) return Outcome.Refused(UiText.res(R.string.pos_offline_short, estimate.cashDue))

        // A local number for the slip and the kitchen; the server assigns the official one at sync (OF6).
        val number = catalog.devicePrefix + "-" + clock().format(LOCAL_NUMBER)
        val dineIn = snapshot.orderType == "DINE-IN"
        val entry = try {
            sync.enqueueCashOrder(
                storeSlug = staff.storeSlug,
                orderType = if (dineIn) "dine_in" else "takeaway",
                tableNumber = snapshot.tableName.takeIf { dineIn && it.isNotBlank() },
                customerName = snapshot.customerName.trim().ifBlank { "Pelanggan Walk-In" },
                items = snapshot.cart.map { OfflineOrderItemPayload(it.product.id, it.quantity, it.notes) },
                cashReceivedIdr = tenderedIdr,
                cashierId = staff.userId,
                shiftId = shiftId,
                catalogVersion = catalog.catalogVersion,
                offlineQueueNumber = number
            )
        } catch (_: OfflineLimitReachedException) {
            return refused(R.string.pos_offline_limit)
        }

        val change = tenderedIdr - estimate.cashDue
        printDispatcher.dispatchReceiptsAndKitchenTickets(
            state = snapshot,
            order = PosOrder(
                id = entry.clientOrderId,
                orderNumber = number,
                queueNumber = number,
                customerName = snapshot.customerName,
                subtotalAmount = estimate.quote.subtotalAmount,
                serviceChargeAmount = estimate.quote.serviceChargeAmount,
                taxAmount = estimate.quote.taxAmount,
                totalAmount = estimate.quote.totalAmount,
                paidAmount = estimate.cashDue
            ),
            cartSnapshot = snapshot.cart,
            tableLabel = printDispatcher.resolveTableLabel(snapshot),
            paymentLabel = OFFLINE_PAYMENT_LABEL,
            cashTenderedIdr = tenderedIdr,
            changeIdr = change,
            roundingIdr = estimate.cashDue - estimate.quote.totalAmount,
            config = printerSettings.getConfig(),
            roundNumber = snapshot.dineInRound
        )
        return Outcome.Queued(number, change)
    }

    private fun refused(id: Int) = Outcome.Refused(UiText.res(id))

    private companion object {
        val LOCAL_NUMBER: DateTimeFormatter = DateTimeFormatter.ofPattern("HHmmss")

        /** Printed in place of the method: the official figures follow from the server (OF6). */
        const val OFFLINE_PAYMENT_LABEL = "TUNAI OFFLINE - ANGKA RESMI MENYUSUL"
    }
}
