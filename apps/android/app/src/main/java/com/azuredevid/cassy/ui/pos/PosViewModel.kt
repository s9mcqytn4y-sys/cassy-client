package com.azuredevid.cassy.ui.pos

import com.azuredevid.cassy.ui.designsystem.toUiText
import com.azuredevid.cassy.ui.designsystem.UiText
import com.azuredevid.cassy.R
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.azuredevid.cassy.core.audio.CassyAudioCue
import com.azuredevid.cassy.core.audio.CassySoundPlayer
import com.azuredevid.cassy.core.hardware.DEFAULT_RECEIPT_FOOTER
import com.azuredevid.cassy.core.hardware.EscPosReceiptBuilder
import com.azuredevid.cassy.core.hardware.KdsTicketRouter
import com.azuredevid.cassy.core.hardware.PrinterPort9100Manager
import com.azuredevid.cassy.core.security.SessionManager
import com.azuredevid.cassy.core.coroutines.runCatchingCancellable
import com.azuredevid.cassy.data.local.OfflineSyncCoordinator
import com.azuredevid.cassy.data.model.CatalogCategory
import com.azuredevid.cassy.data.model.CatalogProduct
import com.azuredevid.cassy.data.model.OrderQuote
import com.azuredevid.cassy.data.network.CassyRealtimeClient
import com.azuredevid.cassy.domain.model.CreatePosOrderInput
import com.azuredevid.cassy.domain.model.CreatePosOrderItemInput
import com.azuredevid.cassy.domain.model.InitiatePosPaymentInput
import com.azuredevid.cassy.domain.model.PosOrder
import com.azuredevid.cassy.domain.model.PosPaymentAttempt
import com.azuredevid.cassy.domain.repository.CatalogRepository
import com.azuredevid.cassy.domain.repository.OrderQuoteRepository
import com.azuredevid.cassy.domain.repository.PosOrderRepository
import com.azuredevid.cassy.domain.repository.PrinterConfig
import com.azuredevid.cassy.domain.repository.PrinterSettingsRepository
import com.azuredevid.cassy.domain.repository.ClosedTab
import com.azuredevid.cassy.domain.repository.PosTable
import com.azuredevid.cassy.domain.repository.StorePrinterRepository
import com.azuredevid.cassy.domain.repository.TabSplit
import com.azuredevid.cassy.domain.repository.TableTabRepository
import com.azuredevid.cassy.domain.repository.toPrinterConfig
import com.azuredevid.cassy.ui.designsystem.CassyToastTone
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

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
    val itemSplit: ItemSplitDraft? = null
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
}

class PosViewModel(
    private val catalogRepository: CatalogRepository,
    private val orderQuoteRepository: OrderQuoteRepository,
    private val posOrderRepository: PosOrderRepository,
    private val printerSettings: PrinterSettingsRepository,
    private val sessionManager: SessionManager,
    private val printerManager: PrinterPort9100Manager = PrinterPort9100Manager(),
    private val realtimeClient: CassyRealtimeClient? = null,
    private val soundPlayer: CassySoundPlayer? = null,
    private val offlineSyncCoordinator: OfflineSyncCoordinator? = null,
    private val tableTabRepository: TableTabRepository? = null,
    private val storePrinters: StorePrinterRepository? = null,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle()
) : ViewModel() {

    private companion object {
        const val KEY_SELECTED_CATEGORY = "pos_selected_category"
        const val MAX_TAB_SPLITS = 20
        const val KEY_SEARCH_QUERY = "pos_search_query"
        const val KEY_ORDER_TYPE = "pos_order_type"
        const val KEY_TABLE_NAME = "pos_table_name"
    }

    private val _uiState = MutableStateFlow(
        PosUiState(
            selectedCategoryId = savedStateHandle.get<String>(KEY_SELECTED_CATEGORY) ?: "all",
            searchQuery = savedStateHandle.get<String>(KEY_SEARCH_QUERY).orEmpty(),
            orderType = savedStateHandle.get<String>(KEY_ORDER_TYPE) ?: "DINE-IN",
            tableName = savedStateHandle.get<String>(KEY_TABLE_NAME).orEmpty()
        )
    )
    val uiState: StateFlow<PosUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<PosUiEffect>()
    val effects: SharedFlow<PosUiEffect> = _effects.asSharedFlow()

    private var quoteJob: Job? = null
    private var qrisPollJob: Job? = null

    init {
        observePrinterConfig()
        observeRealtimeEvents()
        loadInitialData()
        offlineSyncCoordinator?.let { sync ->
            viewModelScope.launch { runCatchingCancellable { sync.syncPendingOrders() } }
        }
    }

    private fun observePrinterConfig() {
        viewModelScope.launch {
            printerSettings.configFlow.collect { config ->
                _uiState.update {
                    it.copy(
                        printerConfig = config,
                        isPrinterConfigured = config.hasCashierPrinter
                    )
                }
            }
        }
    }

    private fun observeRealtimeEvents() {
        val client = realtimeClient ?: return
        viewModelScope.launch {
            client.events.collect { event ->
                // Chime only for a new call; acknowledge and done from another device stay silent.
                if (event.type.equals("service_request.created", ignoreCase = true)) {
                    soundPlayer?.play(CassyAudioCue.CALL_WAITER)
                } else if (event.type.contains("order.created", ignoreCase = true)) {
                    soundPlayer?.play(CassyAudioCue.NEW_QR_ORDER)
                }
                val activeOrder = _uiState.value.pendingOrder ?: return@collect
                if (_uiState.value.activeQrisPayment == null) return@collect
                val matchesOrder = event.payloadJson.contains(activeOrder.id, ignoreCase = true) ||
                    (activeOrder.orderNumber.isNotBlank() && event.payloadJson.contains(activeOrder.orderNumber, ignoreCase = true))
                if (matchesOrder && (event.type.contains("paid", ignoreCase = true) || event.type.contains("settled", ignoreCase = true))) {
                    checkQrisStatusOnce()
                }
            }
        }
    }

    fun loadInitialData() {
        val session = sessionManager.currentSession()
        val storeSlug = session?.staff?.storeSlug
        _uiState.update {
            it.copy(
                storeName = session?.staff?.storeName.orEmpty(),
                cashierName = session?.staff?.fullName.orEmpty()
            )
        }

        if (session == null || storeSlug.isNullOrBlank()) {
            _uiState.update {
                it.copy(
                    isLoadingCatalog = false,
                    catalogError = UiText.res(R.string.pos_msg_store_session_missing)
                )
            }
            return
        }

        viewModelScope.launch {
            // Printers are configured on the server; offline, the last synced copy keeps printing.
            storePrinters?.listForTablet()?.onSuccess { printerSettings.saveConfig(it.toPrinterConfig()) }
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingCatalog = true, catalogError = null) }
            catalogRepository.getStaffCatalog()
                .onSuccess { snapshot ->
                    val activeCategories = snapshot.categories
                        .filter { it.isActive }
                        .sortedBy { it.displayOrder }
                    _uiState.update {
                        it.copy(
                            categories = activeCategories,
                            allProducts = snapshot.products,
                            isLoadingCatalog = false,
                            catalogError = null
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            categories = emptyList(),
                            allProducts = emptyList(),
                            isLoadingCatalog = false,
                            catalogError = err.toUiText(R.string.msg_catalog_load_failed)
                        )
                    }
                }
        }
    }

    fun selectCategory(categoryId: String) {
        savedStateHandle[KEY_SELECTED_CATEGORY] = categoryId
        _uiState.update { it.copy(selectedCategoryId = categoryId) }
    }

    fun updateSearchQuery(query: String) {
        savedStateHandle[KEY_SEARCH_QUERY] = query
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun updateTableName(tableName: String) {
        if (_uiState.value.isCartLocked || _uiState.value.activeTabId != null) return
        val trimmed = tableName.trim()
        savedStateHandle[KEY_TABLE_NAME] = trimmed
        _uiState.update {
            it.copy(
                tableName = trimmed,
                tableId = null,
                pendingOrder = null,
                pendingOrderIdempotencyKey = null
            )
        }
    }

    fun toggleOrderType() {
        if (_uiState.value.isCartLocked || _uiState.value.activeTabId != null) return
        _uiState.update {
            val next = if (it.orderType == "DINE-IN") "TAKEAWAY" else "DINE-IN"
            val nextTable = if (next == "TAKEAWAY") "" else it.tableName
            savedStateHandle[KEY_ORDER_TYPE] = next
            savedStateHandle[KEY_TABLE_NAME] = nextTable
            it.copy(
                orderType = next,
                tableName = nextTable,
                tableId = if (next == "TAKEAWAY") null else it.tableId,
                pendingOrder = null,
                pendingOrderIdempotencyKey = null
            )
        }
        refreshQuote()
    }

    fun addProductToCart(product: CatalogProduct) {
        if (_uiState.value.isCartLocked || _uiState.value.pendingSplitId != null || !product.isAvailable) return
        _uiState.update { state ->
            // Sent lines are already on the kitchen ticket and the server bill; more goes on a new line.
            val existingIndex = state.cart.indexOfFirst { it.product.id == product.id && !it.isSentToKitchen }
            val updatedCart = if (existingIndex >= 0) {
                state.cart.mapIndexed { idx, item ->
                    if (idx == existingIndex) item.copy(quantity = item.quantity + 1) else item
                }
            } else {
                state.cart + CartItem(product = product, quantity = 1)
            }
            state.copy(
                cart = updatedCart,
                pendingOrder = null,
                pendingOrderIdempotencyKey = null,
                pendingRoundKey = null,
                settlementError = null
            )
        }
        refreshQuote()
    }

    fun increaseItemQuantity(productId: String) {
        val product = _uiState.value.cart.firstOrNull { it.product.id == productId }?.product ?: return
        addProductToCart(product)
    }

    fun decreaseItemQuantity(productId: String) {
        if (_uiState.value.isCartLocked || _uiState.value.pendingSplitId != null) return
        _uiState.update { state ->
            // Taking back a sent line is a void with its own PIN flow (TabItemVoidViewModel); only unsent lines shrink here.
            val updatedCart = state.cart.mapNotNull { item ->
                if (item.product.id != productId || item.isSentToKitchen) {
                    item
                } else if (item.quantity > 1) {
                    item.copy(quantity = item.quantity - 1)
                } else {
                    null
                }
            }
            state.copy(
                cart = updatedCart,
                pendingOrder = null,
                pendingOrderIdempotencyKey = null,
                pendingRoundKey = null,
                settlementError = null
            )
        }
        refreshQuote()
    }

    /** Loads the table board for the picker; on failure the free-text table field still works. */
    fun loadTables() {
        val repo = tableTabRepository ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingTables = true) }
            val result = repo.listTables()
            _uiState.update { it.copy(isLoadingTables = false, tables = result.getOrDefault(it.tables)) }
            result.onFailure { err -> _effects.emit(PosUiEffect.ShowToast(err.toUiText(R.string.pos_msg_tables_failed))) }
        }
    }

    /**
     * Binds the order to a board table. A table that already has guests resumes its open tab, and the
     * items already on the server bill come back into the cart as sent lines.
     */
    fun selectTable(table: PosTable) {
        val snapshot = _uiState.value
        if (snapshot.isCartLocked || snapshot.activeTabId != null || snapshot.cart.any { it.isSentToKitchen }) return
        savedStateHandle[KEY_TABLE_NAME] = table.tableNumber
        _uiState.update {
            it.copy(tableId = table.id, tableName = table.tableNumber, pendingOrder = null, pendingOrderIdempotencyKey = null)
        }
        if (table.isFree) return
        val repo = tableTabRepository ?: return
        viewModelScope.launch {
            repo.openTab(UUID.randomUUID().toString(), table.id, table.tableNumber).onSuccess { tab ->
                applyTabOrder(tab)
            }.onFailure { err ->
                _effects.emit(PosUiEffect.ShowToast(err.toUiText(R.string.pos_msg_tab_open_failed)))
            }
        }
    }

    /** Replaces the sent rows with the tab's server lines (after a resume, a round or a void); unsent rows stay. */
    fun applyTabOrder(tab: PosOrder) {
        _uiState.update { state ->
            val products = state.allProducts.associateBy { it.id }
            // shortcut: lines whose product left the catalog are not shown, though the server total still counts them; add a placeholder line if that happens in practice.
            val sent = tab.items.groupBy { it.productId }.mapNotNull { (productId, lines) ->
                products[productId]?.let { CartItem(it, lines.sumOf { l -> l.quantity }, isSentToKitchen = true, sentLines = lines) }
            }
            state.copy(activeTabId = tab.id, cart = sent + state.cart.filterNot { it.isSentToKitchen })
        }
        refreshQuote()
    }

    /** Opens the tab when needed and sends the unsent cart lines as one round; returns the tab id. */
    private suspend fun submitTabRound(snapshot: PosUiState): Result<String> {
        val repo = tableTabRepository ?: return Result.failure(IllegalStateException("table tabs unavailable"))
        val tableId = snapshot.tableId ?: return Result.failure(IllegalStateException("no table selected"))
        val tabId = snapshot.activeTabId ?: run {
            val key = snapshot.pendingTabKey ?: UUID.randomUUID().toString()
            _uiState.update { it.copy(pendingTabKey = key) }
            val tab = repo.openTab(key, tableId, snapshot.tableName).getOrElse { return Result.failure(it) }
            _uiState.update { it.copy(activeTabId = tab.id, pendingTabKey = null) }
            tab.id
        }
        val unsent = snapshot.cart.filterNot { it.isSentToKitchen }
        if (unsent.isEmpty()) return Result.success(tabId)
        // The key survives a failed send so a retry cannot add the same round twice; any cart edit resets it.
        val roundKey = snapshot.pendingRoundKey ?: UUID.randomUUID().toString()
        _uiState.update { it.copy(pendingRoundKey = roundKey) }
        repo.submitRound(tabId, roundKey, unsent.map { CreatePosOrderItemInput(it.product.id, it.quantity, it.notes) })
            .getOrElse { return Result.failure(it) }
        _uiState.update { state -> state.copy(pendingRoundKey = null, cart = state.cart.map { it.copy(isSentToKitchen = true) }) }
        // Pick up the server line ids of the new round so its lines can be voided later.
        repo.getTab(tabId).onSuccess { applyTabOrder(it) }
        return Result.success(tabId)
    }

    /**
     * Creates (or reuses) the pay-first order for the cart and opens the split dialog for it. The order is
     * priced and stored by the server exactly like a normal sale; the dialog then divides and collects it.
     */
    fun startSplitBill() {
        val snapshot = _uiState.value
        val quote = snapshot.quote ?: return
        // A table tab is divided when it closes (PosTabSplitSection); this dialog serves pay-first orders.
        if (snapshot.cart.isEmpty() || snapshot.isSubmittingOrder || snapshot.tableId != null) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmittingOrder = true, settlementError = null) }
            ensureOrderCreated(snapshot, quote).fold(
                onSuccess = { order -> _uiState.update { it.copy(isSubmittingOrder = false, splitOrderId = order.id) } },
                onFailure = { err ->
                    val message = err.toUiText(R.string.pos_msg_split_order_failed)
                    _uiState.update { it.copy(isSubmittingOrder = false, settlementError = message) }
                    _effects.emit(PosUiEffect.ShowToast(message, CassyToastTone.ERROR))
                }
            )
        }
    }

    /** Closes the split dialog; the order stays pending and the cart keeps it for a later resume. */
    fun closeSplitBill() {
        _uiState.update { it.copy(splitOrderId = null) }
    }

    /**
     * Called when the server reports the divided order fully paid: prints the receipt and the kitchen
     * tickets (the backend has just created the KDS tickets) and clears the cart. Bills resumed from the
     * open-bills list are not tied to the cart, so the cart is left alone for them.
     */
    fun onSplitBillSettled(order: PosOrder) {
        val snapshot = _uiState.value
        val belongsToCart = snapshot.pendingOrder?.id == order.id
        viewModelScope.launch {
            if (belongsToCart) {
                dispatchReceiptsAndKitchenTickets(
                    order = order,
                    cartSnapshot = snapshot.cart,
                    tableLabel = resolveTableLabel(snapshot),
                    paymentLabel = "BAGI TAGIHAN",
                    cashTenderedIdr = null,
                    changeIdr = null,
                    roundingIdr = 0L,
                    config = printerSettings.getConfig(),
                    roundNumber = snapshot.dineInRound
                )
                clearCartAfterPayment()
            }
            _uiState.update { it.copy(splitOrderId = null) }
            val orderNo = order.orderNumber.ifBlank { order.id.takeLast(8).uppercase() }
            _effects.emit(PosUiEffect.CashPaymentCompleted(orderNo))
            _effects.emit(PosUiEffect.ShowToast(UiText.res(R.string.pos_msg_split_bill_paid, orderNo), CassyToastTone.SUCCESS))
        }
    }

    /** Kicks the cash drawer after a cash share, when a cashier printer is configured. */
    fun kickDrawerForSplitCash() {
        viewModelScope.launch {
            val config = printerSettings.getConfig()
            if (config.hasCashierPrinter) printerManager.openCashDrawer(config.cashierPrinterIp, config.cashierPrinterPort)
        }
    }

    fun openCashModal() {
        if (_uiState.value.cart.isEmpty() || _uiState.value.quote == null) return
        _uiState.update { it.copy(showCashModal = true, settlementError = null) }
    }

    fun closeCashModal() {
        if (_uiState.value.isSubmittingOrder) return
        _uiState.update { it.copy(showCashModal = false, settlementError = null) }
    }

    fun submitCashPayment(tenderedAmount: Long, drawerId: String? = null) {
        val snapshot = _uiState.value
        val quote = snapshot.quote ?: return
        if (snapshot.cart.isEmpty() || snapshot.isSubmittingOrder) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmittingOrder = true, settlementError = null) }
            val orderResult = ensureOrderCreated(snapshot, quote)
            val order = orderResult.getOrElse { err ->
                _uiState.update {
                    it.copy(
                        isSubmittingOrder = false,
                        settlementError = err.toUiText(R.string.pos_msg_order_send_failed)
                    )
                }
                _effects.emit(PosUiEffect.ShowToast(err.toUiText(R.string.pos_msg_order_send_failed)))
                return@launch
            }

            val paymentResult = posOrderRepository.initiatePayment(
                InitiatePosPaymentInput(
                    orderId = order.id,
                    idempotencyKey = UUID.randomUUID().toString(),
                    method = "cash",
                    cashTenderedAmount = tenderedAmount,
                    drawerId = drawerId,
                    splitId = _uiState.value.pendingSplitId
                )
            )
            paymentResult.onSuccess { res ->
                val settledOrder = res.order ?: order
                val config = printerSettings.getConfig()
                if (res.openDrawer && config.hasCashierPrinter) {
                    printerManager.openCashDrawer(config.cashierPrinterIp, config.cashierPrinterPort)
                }
                if (!settlesWholeBill(settledOrder)) {
                    advanceToNextSplit(
                        paymentLabel = "TUNAI",
                        cashTenderedIdr = res.payment.cashTenderedAmount ?: tenderedAmount,
                        changeIdr = res.payment.changeGivenAmount
                    )
                    return@onSuccess
                }
                dispatchReceiptsAndKitchenTickets(
                    order = settledOrder,
                    cartSnapshot = snapshot.cart,
                    tableLabel = resolveTableLabel(snapshot),
                    paymentLabel = "TUNAI",
                    cashTenderedIdr = res.payment.cashTenderedAmount ?: tenderedAmount,
                    changeIdr = res.payment.changeGivenAmount ?: (tenderedAmount - settledOrder.totalAmount).coerceAtLeast(0L),
                    roundingIdr = res.payment.roundingAmount ?: 0L,
                    config = config,
                    roundNumber = snapshot.dineInRound
                )
                clearCartAfterPayment()
                val orderNo = settledOrder.orderNumber.ifBlank { order.id.takeLast(8).uppercase() }
                soundPlayer?.play(CassyAudioCue.ORDER_PAID)
                _effects.emit(PosUiEffect.CashPaymentCompleted(orderNo))
                _effects.emit(PosUiEffect.ShowToast(UiText.res(R.string.pos_msg_cash_done, orderNo)))
            }.onFailure { err ->
                soundPlayer?.play(CassyAudioCue.SOFT_ERROR)
                _uiState.update {
                    it.copy(
                        isSubmittingOrder = false,
                        settlementError = err.toUiText(R.string.pos_msg_cash_failed)
                    )
                }
                _effects.emit(PosUiEffect.ShowToast(err.toUiText(R.string.pos_msg_cash_failed)))
            }
        }
    }

    fun startQrisPayment(drawerId: String? = null) {
        val snapshot = _uiState.value
        val quote = snapshot.quote ?: return
        if (snapshot.cart.isEmpty() || snapshot.isSubmittingOrder) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmittingOrder = true, settlementError = null) }
            val orderResult = ensureOrderCreated(snapshot, quote)
            val order = orderResult.getOrElse { err ->
                _uiState.update {
                    it.copy(
                        isSubmittingOrder = false,
                        settlementError = err.toUiText(R.string.pos_msg_qris_order_failed)
                    )
                }
                _effects.emit(PosUiEffect.ShowToast(err.toUiText(R.string.pos_msg_qris_order_failed)))
                return@launch
            }

            posOrderRepository.initiatePayment(
                InitiatePosPaymentInput(
                    orderId = order.id,
                    idempotencyKey = UUID.randomUUID().toString(),
                    method = "qris",
                    drawerId = drawerId,
                    splitId = _uiState.value.pendingSplitId
                )
            ).onSuccess { res ->
                val updatedOrder = res.order ?: order
                _uiState.update {
                    it.copy(
                        isSubmittingOrder = false,
                        pendingOrder = updatedOrder,
                        activeQrisPayment = res.payment
                    )
                }
                if (updatedOrder.paymentStatus.equals("paid", ignoreCase = true) ||
                    res.payment.status.equals("settled", ignoreCase = true)
                ) {
                    finalizeQrisSettlement(updatedOrder)
                } else {
                    startQrisPolling()
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isSubmittingOrder = false,
                        settlementError = err.toUiText(R.string.pos_msg_qris_bill_failed)
                    )
                }
                _effects.emit(PosUiEffect.ShowToast(err.toUiText(R.string.pos_msg_qris_bill_failed)))
            }
        }
    }

    fun refreshQrisStatus() {
        viewModelScope.launch {
            checkQrisStatusOnce()
        }
    }

    fun dismissQrisModal() {
        qrisPollJob?.cancel()
        qrisPollJob = null
        _uiState.update {
            it.copy(
                activeQrisPayment = null,
                isCheckingQrisStatus = false
            )
        }
    }

    private fun startQrisPolling() {
        qrisPollJob?.cancel()
        qrisPollJob = viewModelScope.launch {
            while (isActive && _uiState.value.activeQrisPayment != null) {
                delay(3_000L)
                checkQrisStatusOnce()
            }
        }
    }

    private suspend fun checkQrisStatusOnce() {
        val order = _uiState.value.pendingOrder ?: return
        if (_uiState.value.activeQrisPayment == null) return
        _uiState.update { it.copy(isCheckingQrisStatus = true) }
        posOrderRepository.getOrder(order.id)
            .onSuccess { detail ->
                _uiState.update { it.copy(isCheckingQrisStatus = false, pendingOrder = detail.order) }
                val attemptId = _uiState.value.activeQrisPayment?.id
                // With shares, an earlier settled share must not finish this one: match the open attempt.
                val paid = detail.order.paymentStatus.equals("paid", ignoreCase = true) ||
                    detail.payments.any {
                        it.id == attemptId &&
                            (it.status.equals("settled", ignoreCase = true) || it.status.equals("paid", ignoreCase = true))
                    }
                if (paid) {
                    finalizeQrisSettlement(detail.order)
                }
            }
            .onFailure {
                _uiState.update { it.copy(isCheckingQrisStatus = false) }
            }
    }

    private suspend fun finalizeQrisSettlement(order: PosOrder) {
        if (!settlesWholeBill(order)) {
            qrisPollJob?.cancel()
            qrisPollJob = null
            advanceToNextSplit(paymentLabel = "QRIS")
            return
        }
        val snapshot = _uiState.value
        val config = printerSettings.getConfig()
        dispatchReceiptsAndKitchenTickets(
            order = order,
            cartSnapshot = snapshot.cart,
            tableLabel = resolveTableLabel(snapshot),
            paymentLabel = "QRIS DINAMIS",
            cashTenderedIdr = null,
            changeIdr = null,
            roundingIdr = 0L,
            config = config,
            roundNumber = snapshot.dineInRound
        )
        clearCartAfterPayment()
        val orderNo = order.orderNumber.ifBlank { order.id.takeLast(8).uppercase() }
        soundPlayer?.play(CassyAudioCue.ORDER_PAID)
        _effects.emit(PosUiEffect.QrisPaymentCompleted(orderNo))
        _effects.emit(PosUiEffect.ShowToast(UiText.res(R.string.pos_msg_qris_paid, orderNo)))
    }

    fun kickDrawerManual() {
        viewModelScope.launch {
            val config = printerSettings.getConfig()
            if (!config.hasCashierPrinter) {
                _effects.emit(PosUiEffect.ShowToast(UiText.res(R.string.msg_cashier_printer_missing)))
                return@launch
            }
            printerManager.openCashDrawer(config.cashierPrinterIp, config.cashierPrinterPort)
                .onSuccess {
                    _effects.emit(PosUiEffect.ShowToast(UiText.res(R.string.pos_msg_drawer_opened)))
                }
                .onFailure {
                    _effects.emit(PosUiEffect.ShowToast(UiText.res(R.string.pos_msg_lan_printer_unreachable, config.cashierPrinterIp)))
                }
        }
    }

    fun sendToKitchen() {
        val snapshot = _uiState.value
        if (snapshot.cart.isEmpty()) return
        viewModelScope.launch {
            if (snapshot.tableId != null) {
                submitTabRound(snapshot).onFailure { err ->
                    _effects.emit(PosUiEffect.ShowToast(err.toUiText(R.string.pos_msg_round_failed)))
                    return@launch
                }
            }
            val config = printerSettings.getConfig()
            val stations = buildStationConfigs(config)
            if (stations.isEmpty()) {
                _effects.emit(PosUiEffect.ShowToast(UiText.res(R.string.pos_msg_kitchen_printer_missing)))
                return@launch
            }
            val router = KdsTicketRouter(printerManager)
            val result = router.routeOrderDynamic(
                orderNumber = snapshot.pendingOrder?.orderNumber?.ifBlank { null } ?: "DAPUR-R${snapshot.dineInRound}",
                tableOrQueue = resolveTableLabel(snapshot),
                items = snapshot.cart.toStationItems(),
                stations = stations,
                cashierPrinterIp = config.cashierPrinterIp,
                deliveryMode = KdsTicketRouter.OrderDeliveryMode.DINE_IN,
                roundNumber = snapshot.dineInRound
            )
            markCartSentToKitchen()
            _uiState.update {
                it.copy(
                    failoverAlert = if (result.hasAnyFailover) {
                        UiText.res(R.string.pos_msg_station_failover)
                    } else null
                )
            }
            if (result.errors.isEmpty()) {
                _effects.emit(PosUiEffect.ShowToast(UiText.res(R.string.pos_msg_kitchen_round_sent, snapshot.dineInRound)))
            } else {
                _effects.emit(PosUiEffect.ShowToast(UiText.Raw(result.errors.first())))
            }
        }
    }

    fun reprintStations() {
        val snapshot = _uiState.value
        if (snapshot.cart.isEmpty()) return
        viewModelScope.launch {
            val config = printerSettings.getConfig()
            val stations = buildStationConfigs(config)
            if (stations.isEmpty()) {
                _effects.emit(PosUiEffect.ShowToast(UiText.res(R.string.pos_msg_kitchen_printer_missing_short)))
                return@launch
            }
            val router = KdsTicketRouter(printerManager)
            val result = router.routeOrderDynamic(
                orderNumber = snapshot.pendingOrder?.orderNumber?.ifBlank { null } ?: "REPRINT-R${snapshot.dineInRound}",
                tableOrQueue = resolveTableLabel(snapshot),
                items = snapshot.cart.toStationItems(),
                stations = stations,
                cashierPrinterIp = config.cashierPrinterIp,
                deliveryMode = if (snapshot.orderType == "DINE-IN") {
                    KdsTicketRouter.OrderDeliveryMode.DINE_IN
                } else {
                    KdsTicketRouter.OrderDeliveryMode.TAKEAWAY
                },
                roundNumber = snapshot.dineInRound
            )
            if (result.errors.isEmpty()) {
                _effects.emit(PosUiEffect.ShowToast(UiText.res(R.string.pos_msg_reprint_done)))
            } else {
                _effects.emit(PosUiEffect.ShowToast(UiText.Raw(result.errors.first())))
            }
        }
    }

    fun markCartSentToKitchen() {
        _uiState.update { state ->
            state.copy(
                dineInRound = state.dineInRound + 1,
                cart = state.cart.map { it.copy(isSentToKitchen = true) }
            )
        }
    }

    fun clearCartAfterPayment() {
        quoteJob?.cancel()
        qrisPollJob?.cancel()
        qrisPollJob = null
        _uiState.update {
            it.copy(
                cart = emptyList(),
                quote = null,
                quoteError = null,
                isQuoting = false,
                dineInRound = 1,
                showCashModal = false,
                isSubmittingOrder = false,
                settlementError = null,
                pendingOrder = null,
                pendingOrderIdempotencyKey = null,
                activeQrisPayment = null,
                isCheckingQrisStatus = false,
                failoverAlert = null,
                activeTabId = null,
                pendingTabKey = null,
                pendingRoundKey = null,
                pendingSplitId = null,
                tabSplitCount = 1,
                tabSplits = emptyList(),
                // A paid tab frees its board table; a free-text label stays for the next walk-in.
                tableId = null,
                tableName = if (it.tableId != null) "" else it.tableName,
                splitOrderId = null
            )
        }
    }

    private suspend fun ensureOrderCreated(snapshot: PosUiState, quote: OrderQuote): Result<PosOrder> {
        val existing = snapshot.pendingOrder
        if (existing != null) {
            return Result.success(existing)
        }
        if (snapshot.tableId != null) {
            val repo = tableTabRepository ?: return Result.failure(IllegalStateException("table tabs unavailable"))
            return closeTab(repo, snapshot) { repo.closeTab(it) }
        }
        val key = snapshot.pendingOrderIdempotencyKey ?: UUID.randomUUID().toString()
        val orderTypeApi = if (snapshot.orderType == "DINE-IN") "dine_in" else "takeaway"
        val customer = snapshot.customerName.trim().ifBlank {
            if (orderTypeApi == "dine_in" && snapshot.tableName.isNotBlank()) {
                "Tamu ${snapshot.tableName}"
            } else {
                "Pelanggan Walk-In"
            }
        }
        val result = posOrderRepository.createOrder(
            CreatePosOrderInput(
                idempotencyKey = key,
                tableNumber = if (orderTypeApi == "dine_in") snapshot.tableName.ifBlank { null } else null,
                customerName = customer,
                orderType = orderTypeApi,
                expectedTotal = quote.totalAmount,
                expectedQuoteFingerprint = quote.quoteFingerprint.ifBlank { null },
                items = snapshot.cart.map {
                    CreatePosOrderItemInput(
                        productId = it.product.id,
                        quantity = it.quantity,
                        notes = it.notes
                    )
                }
            )
        )
        result.onSuccess { created ->
            _uiState.update { it.copy(pendingOrder = created, pendingOrderIdempotencyKey = key) }
        }
        return result
    }

    /** Picks how many equal shares the table bill is divided into; fixed once the tab is closed. */
    fun setTabSplitCount(count: Int) {
        val state = _uiState.value
        if (state.tableId == null || state.pendingOrder != null || state.isCartLocked) return
        _uiState.update { it.copy(tabSplitCount = count.coerceIn(1, MAX_TAB_SPLITS)) }
    }

    /** Closes the table bill into equal shares so each guest can pay their own share. */
    fun splitTab() {
        val snapshot = _uiState.value
        val repo = tableTabRepository ?: return
        if (snapshot.tableId == null || snapshot.tabSplitCount < 2 || snapshot.pendingOrder != null ||
            snapshot.cart.isEmpty() || snapshot.isCartLocked
        ) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmittingOrder = true, settlementError = null) }
            closeTab(repo, snapshot) { repo.closeTab(it, snapshot.tabSplitCount) }.onFailure { err ->
                _effects.emit(PosUiEffect.ShowToast(err.toUiText(R.string.pos_msg_split_failed), CassyToastTone.ERROR))
            }
            _uiState.update { it.copy(isSubmittingOrder = false) }
        }
    }

    /** Starts a split by item: every unit of every sent line waits for a guest. */
    fun openItemSplit() {
        val state = _uiState.value
        if (!state.canSplitByItem) return
        val units = state.cart.flatMap { row ->
            row.sentLines.flatMap { line -> List(line.quantity) { SplitUnit(line.id, row.product.name) } }
        }
        _uiState.update { it.copy(itemSplit = ItemSplitDraft(units)) }
    }

    fun dismissItemSplit() {
        if (_uiState.value.isSubmittingOrder) return
        _uiState.update { it.copy(itemSplit = null) }
    }

    fun setItemSplitGuests(count: Int) = editItemSplit { draft ->
        val guests = count.coerceIn(2, minOf(MAX_TAB_SPLITS, draft.units.size))
        draft.copy(guests = guests, owners = draft.owners.map { owner -> owner?.takeIf { it <= guests } })
    }

    fun assignUnit(index: Int, guest: Int) = editItemSplit { draft ->
        if (index !in draft.owners.indices || guest !in 1..draft.guests) draft
        else draft.copy(owners = draft.owners.toMutableList().also { it[index] = guest })
    }

    /** Closes the tab with one share per guest; the server checks the units and prices each share. */
    fun confirmItemSplit() {
        val snapshot = _uiState.value
        val draft = snapshot.itemSplit ?: return
        val repo = tableTabRepository ?: return
        if (!draft.canConfirm || snapshot.isSubmittingOrder) return
        val shares = (1..draft.guests).map { guest ->
            draft.units.filterIndexed { i, _ -> draft.owners[i] == guest }.groupingBy { it.lineId }.eachCount()
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmittingOrder = true, settlementError = null) }
            closeTab(repo, snapshot) { tabId -> repo.closeTabByItem(tabId, shares) }
                .onSuccess { _uiState.update { it.copy(itemSplit = null) } }
                .onFailure { err -> _effects.emit(PosUiEffect.ShowToast(err.toUiText(R.string.pos_msg_split_failed), CassyToastTone.ERROR)) }
            _uiState.update { it.copy(isSubmittingOrder = false) }
        }
    }

    private fun editItemSplit(change: (ItemSplitDraft) -> ItemSplitDraft) {
        if (_uiState.value.isSubmittingOrder) return
        _uiState.update { state -> state.itemSplit?.let { state.copy(itemSplit = change(it)) } ?: state }
    }

    /** Selects which unpaid share the next payment covers. */
    fun selectSplit(splitId: String) {
        val state = _uiState.value
        if (state.isCartLocked || state.tabSplits.none { it.id == splitId && !it.isPaid }) return
        _uiState.update { it.copy(pendingSplitId = splitId) }
    }

    private suspend fun closeTab(
        repo: TableTabRepository,
        snapshot: PosUiState,
        close: suspend (tabId: String) -> Result<ClosedTab>
    ): Result<PosOrder> {
        val tabId = submitTabRound(snapshot).getOrElse { return Result.failure(it) }
        val closed = close(tabId).getOrElse { return Result.failure(it) }
        // A closed tab cannot be voided, so its rows drop the line ids that enable the void button.
        _uiState.update { state ->
            state.copy(
                pendingOrder = closed.order,
                pendingSplitId = closed.splits.firstOrNull { !it.isPaid }?.id,
                tabSplits = closed.splits,
                cart = state.cart.map { it.copy(sentLines = emptyList()) }
            )
        }
        return Result.success(closed.order)
    }

    private fun settlesWholeBill(order: PosOrder): Boolean {
        val state = _uiState.value
        return order.paymentStatus.equals("paid", ignoreCase = true) ||
            state.tabSplits.none { !it.isPaid && it.id != state.pendingSplitId }
    }

    /** A share paid before the last prints its own short receipt; the full receipt prints when the bill is settled. */
    private suspend fun advanceToNextSplit(paymentLabel: String, cashTenderedIdr: Long? = null, changeIdr: Long? = null) {
        val state = _uiState.value
        val paid = state.selectedSplit ?: return
        printShareReceipt(state, paid, paymentLabel, cashTenderedIdr, changeIdr)
        val splits = state.tabSplits.map {
            if (it.id == paid.id) it.copy(status = "paid", paidAmount = it.totalAmount) else it
        }
        _uiState.update {
            it.copy(
                tabSplits = splits,
                pendingSplitId = splits.firstOrNull { split -> !split.isPaid }?.id,
                showCashModal = false,
                isSubmittingOrder = false,
                activeQrisPayment = null,
                isCheckingQrisStatus = false,
                settlementError = null
            )
        }
        soundPlayer?.play(CassyAudioCue.ORDER_PAID)
        _effects.emit(PosUiEffect.ShowToast(UiText.res(R.string.pos_msg_split_paid, paid.number)))
    }

    private suspend fun printShareReceipt(
        state: PosUiState,
        share: TabSplit,
        paymentLabel: String,
        cashTenderedIdr: Long?,
        changeIdr: Long?
    ) {
        val config = printerSettings.getConfig()
        if (!config.hasCashierPrinter) return
        val paperWidth = if (config.paperWidthMm == 58) {
            EscPosReceiptBuilder.PaperWidth.WIDTH_58MM
        } else {
            EscPosReceiptBuilder.PaperWidth.WIDTH_80MM
        }
        val order = state.pendingOrder
        val bytes = EscPosReceiptBuilder(paperWidth).buildShareReceipt(
            storeName = state.storeName.ifBlank { "KASIR POS" },
            orderId = order?.orderNumber?.ifBlank { order.id } ?: share.id,
            tableName = resolveTableLabel(state),
            cashierName = state.cashierName.ifBlank { "Kasir" },
            shareNumber = share.number,
            shareCount = state.tabSplits.size,
            shareTotalIdr = share.totalAmount,
            paymentMethod = paymentLabel,
            cashTenderedIdr = cashTenderedIdr,
            changeIdr = changeIdr ?: cashTenderedIdr?.let { (it - share.totalAmount).coerceAtLeast(0L) }
        ).repeatCopies(config.receiptCopies)
        // A failed print must not undo a recorded payment; the cashier can reprint from the final receipt.
        printerManager.printRawBytes(config.cashierPrinterIp, bytes, config.cashierPrinterPort)
    }

    private suspend fun dispatchReceiptsAndKitchenTickets(
        order: PosOrder,
        cartSnapshot: List<CartItem>,
        tableLabel: String,
        paymentLabel: String,
        cashTenderedIdr: Long?,
        changeIdr: Long?,
        roundingIdr: Long,
        config: PrinterConfig,
        roundNumber: Int
    ) {
        val stations = buildStationConfigs(config)
        val hasAnyPrinter = config.hasCashierPrinter || stations.isNotEmpty()
        if (!hasAnyPrinter) return

        val paperWidth = if (config.paperWidthMm == 58) {
            EscPosReceiptBuilder.PaperWidth.WIDTH_58MM
        } else {
            EscPosReceiptBuilder.PaperWidth.WIDTH_80MM
        }
        val receiptItems = if (order.items.isNotEmpty()) {
            order.items.map {
                EscPosReceiptBuilder.ReceiptItem(
                    name = it.snapshotName,
                    qty = it.quantity,
                    priceIdr = if (it.snapshotPrice > 0L) it.snapshotPrice else it.unitBasePrice
                )
            }
        } else {
            cartSnapshot.map {
                EscPosReceiptBuilder.ReceiptItem(it.product.name, it.quantity, it.product.basePriceIdr)
            }
        }

        val receiptBytes = if (config.hasCashierPrinter) {
            EscPosReceiptBuilder(paperWidth).buildStoreReceipt(
                storeName = _uiState.value.storeName.ifBlank { "KASIR POS" },
                storeAddress = "",
                orderId = order.orderNumber.ifBlank { order.id },
                tableName = tableLabel,
                cashierName = _uiState.value.cashierName.ifBlank { "Kasir" },
                items = receiptItems,
                subtotalIdr = order.subtotalAmount,
                pb1TaxIdr = order.taxAmount,
                grandTotalIdr = order.totalAmount,
                paymentMethod = paymentLabel,
                cashTenderedIdr = cashTenderedIdr,
                changeIdr = changeIdr,
                serviceChargeIdr = order.serviceChargeAmount,
                discountIdr = order.discountAmount,
                roundingIdr = roundingIdr,
                footerNotes = config.receiptFooter.ifBlank { DEFAULT_RECEIPT_FOOTER }
            ).repeatCopies(config.receiptCopies)
        } else {
            null
        }

        val router = KdsTicketRouter(printerManager)
        val deliveryMode = if (order.orderType.equals("takeaway", ignoreCase = true)) {
            KdsTicketRouter.OrderDeliveryMode.TAKEAWAY
        } else {
            KdsTicketRouter.OrderDeliveryMode.DINE_IN
        }
        val result = router.routeOrderDynamic(
            orderNumber = order.orderNumber.ifBlank { order.id.takeLast(8).uppercase() },
            tableOrQueue = tableLabel,
            // Rows already sent (tab rounds, an earlier "send to kitchen") were printed then; paying prints only the rest.
            items = cartSnapshot.filterNot { it.isSentToKitchen }.toStationItems(),
            stations = stations,
            cashierPrinterIp = config.cashierPrinterIp,
            cashierPrinterPort = config.cashierPrinterPort,
            cashierReceiptBytes = receiptBytes,
            deliveryMode = deliveryMode,
            roundNumber = roundNumber
        )
        if (result.hasAnyFailover) {
            _uiState.update {
                it.copy(failoverAlert = UiText.res(R.string.pos_msg_station_failover))
            }
        }
    }

    private fun buildStationConfigs(config: PrinterConfig): List<KdsTicketRouter.StationPrinterConfig> {
        val fallback = config.cashierPrinterIp.takeIf { it.isNotBlank() }
        val list = mutableListOf<KdsTicketRouter.StationPrinterConfig>()
        if (config.barPrinterIp.isNotBlank()) {
            list.add(KdsTicketRouter.StationPrinterConfig("bar", "BARISTA", config.barPrinterIp, fallback, config.barPrinterPort))
        }
        if (config.kitchenPrinterIp.isNotBlank()) {
            list.add(KdsTicketRouter.StationPrinterConfig("kitchen", "DAPUR PANAS", config.kitchenPrinterIp, fallback, config.kitchenPrinterPort))
        }
        if (config.expediterPrinterIp.isNotBlank()) {
            list.add(KdsTicketRouter.StationPrinterConfig("runner", "RUNNER / EXPO", config.expediterPrinterIp, fallback, config.expediterPrinterPort))
        }
        return list
    }

    /** Each receipt ends with its own cut, so back-to-back copies print as separate slips. */
    private fun ByteArray.repeatCopies(copies: Int): ByteArray {
        val n = copies.coerceIn(1, 5)
        return if (n == 1) this else ByteArray(size * n) { this[it % size] }
    }

    private fun resolveTableLabel(state: PosUiState): String {
        return if (state.orderType == "TAKEAWAY") {
            "Takeaway"
        } else {
            state.tableName.ifBlank { "Dine-In" }
        }
    }

    private fun List<CartItem>.toStationItems(): List<KdsTicketRouter.StationItem> = map {
        KdsTicketRouter.StationItem(
            name = it.product.name,
            quantity = it.quantity,
            prepStation = it.product.prepStation,
            notes = it.notes
        )
    }

    private fun refreshQuote() {
        quoteJob?.cancel()
        val snapshot = _uiState.value
        if (snapshot.cart.isEmpty()) {
            _uiState.update { it.copy(quote = null, isQuoting = false, quoteError = null) }
            return
        }

        quoteJob = viewModelScope.launch {
            _uiState.update { it.copy(isQuoting = true, quoteError = null) }
            orderQuoteRepository.quote(quoteRequestOf(snapshot.cart, snapshot.orderType))
                .onSuccess { quote ->
                    _uiState.update { it.copy(quote = quote, isQuoting = false, quoteError = null) }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            quote = null,
                            isQuoting = false,
                            quoteError = err.toUiText(R.string.pos_msg_quote_failed)
                        )
                    }
                }
        }
    }

    override fun onCleared() {
        quoteJob?.cancel()
        qrisPollJob?.cancel()
        super.onCleared()
    }
}
