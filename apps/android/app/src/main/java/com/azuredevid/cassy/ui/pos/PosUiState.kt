package com.azuredevid.cassy.ui.pos

import androidx.compose.runtime.Immutable
import com.azuredevid.cassy.data.model.CatalogCategory
import com.azuredevid.cassy.data.model.CatalogProduct
import com.azuredevid.cassy.data.model.OrderQuote
import com.azuredevid.cassy.domain.model.PosOrder
import com.azuredevid.cassy.domain.model.PosPaymentAttempt
import com.azuredevid.cassy.domain.repository.PosCustomerStamps
import com.azuredevid.cassy.domain.repository.PosSalesChannel
import com.azuredevid.cassy.domain.repository.PosTable
import com.azuredevid.cassy.domain.repository.PrinterConfig
import com.azuredevid.cassy.domain.repository.TabSplit
import com.azuredevid.cassy.ui.designsystem.CassyToastTone
import com.azuredevid.cassy.ui.designsystem.UiText

@Immutable
data class PosUiState(
    val categories: List<CatalogCategory> = emptyList(),
    val allProducts: List<CatalogProduct> = emptyList(),
    val isLoadingCatalog: Boolean = true,
    val catalogError: UiText? = null,
    val selectedCategoryId: String = "all",
    val searchQuery: String = "",
    val cart: List<CartItem> = emptyList(),
    val orderType: String = "DINE-IN",
    val tableName: String = "",
    val customerName: String = "",
    val dineInRound: Int = 1,
    val quote: OrderQuote? = null,
    val isQuoting: Boolean = false,
    val quoteError: UiText? = null,
    val storeName: String = "",
    val cashierName: String = "",
    val audioCueMuted: Boolean = false,
    val printerConfig: PrinterConfig = PrinterConfig(),
    val isPrinterConfigured: Boolean = false,
    val failoverAlert: UiText? = null,
    val isSubmittingOrder: Boolean = false,
    val settlementError: UiText? = null,
    val showCashModal: Boolean = false,
    /** Pay-first order open in the split settlement dialog (PR 9), or null. */
    val splitOrderId: String? = null,
    val pendingOrder: PosOrder? = null,
    val pendingOrderIdempotencyKey: String? = null,
    val activeQrisPayment: PosPaymentAttempt? = null,
    val isCheckingQrisStatus: Boolean = false,
    // Open bill (tab) on a table picked from the board; a null tableId keeps the free-text table flow.
    val tables: List<PosTable> = emptyList(),
    val isLoadingTables: Boolean = false,
    val tableId: String? = null,
    val activeTabId: String? = null,
    val pendingTabKey: String? = null,
    val pendingRoundKey: String? = null,
    val pendingSplitId: String? = null,
    // Equal split of a table tab: the count is picked before closing, the shares come back from the server.
    val tabSplitCount: Int = 1,
    val tabSplits: List<TabSplit> = emptyList(),
    val itemSplit: ItemSplitDraft? = null,
    // Sales channels & customer loyalty stamps (Operational endpoints wired in Pre-A3)
    val salesChannels: List<PosSalesChannel> = emptyList(),
    val selectedSalesChannelId: String? = null,
    val matchedCustomerStamps: PosCustomerStamps? = null,
    val lastSettledOrderId: String? = null
) {
    val displayedProducts: List<CatalogProduct>
        get() {
            val baseList = if (selectedCategoryId == "all") {
                allProducts
            } else {
                allProducts.filter { it.categoryId == selectedCategoryId }
            }
            return if (searchQuery.isBlank()) {
                baseList
            } else {
                baseList.filter {
                    it.name.contains(searchQuery, ignoreCase = true) ||
                        it.description.contains(searchQuery, ignoreCase = true)
                }
            }
        }

    val isCartLocked: Boolean
        get() = activeQrisPayment != null || isSubmittingOrder

    /** The share being paid, or null when the bill is not divided. */
    val selectedSplit: TabSplit?
        get() = tabSplits.takeIf { it.size > 1 }?.firstOrNull { it.id == pendingSplitId }

    /** Splitting by item needs every row on the server (line ids) and at least two units to share. */
    val canSplitByItem: Boolean
        get() = tableId != null && pendingOrder == null && !isCartLocked && cart.isNotEmpty() &&
            cart.all { it.isSentToKitchen && it.sentLines.isNotEmpty() } && cart.sumOf { it.quantity } >= 2

    /** True when the current table tab was closed into pending_payment and can be unlocked via `/reopen`. */
    val canReopenClosedTab: Boolean
        get() = tableId != null && pendingOrder != null && tabSplits.all { !it.isPaid } && !isSubmittingOrder

    /** What the next payment must cover: the selected share, else the quoted bill. */
    val payableAmount: Long?
        get() = selectedSplit?.remainingAmount ?: quote?.totalAmount
}

/** One unit of a sent tab line, waiting for a guest when the bill is split by item. */
@Immutable
data class SplitUnit(val lineId: String, val name: String)

/** A split by item in progress: [owners] holds the guest (1-based) of each unit, null until picked. */
@Immutable
data class ItemSplitDraft(
    val units: List<SplitUnit>,
    val guests: Int = 2,
    val owners: List<Int?> = units.map { null }
) {
    val isFullyAssigned: Boolean get() = owners.none { it == null }
    /** Every unit has a guest and every guest pays for something, as the server requires. */
    val canConfirm: Boolean get() = isFullyAssigned && (1..guests).all { it in owners }
}

sealed interface PosUiEffect {
    data class ShowToast(
        val message: UiText,
        val tone: CassyToastTone = CassyToastTone.INFO
    ) : PosUiEffect
    data class CashPaymentCompleted(val orderNumber: String) : PosUiEffect
    data class QrisPaymentCompleted(val orderNumber: String) : PosUiEffect
    data class DigitalReceiptSent(val recipientMasked: String, val channel: String) : PosUiEffect
}
