package com.azuredevid.cassy.ui.pos

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.azuredevid.cassy.R
import com.azuredevid.cassy.core.audio.CassyAudioCue
import com.azuredevid.cassy.core.audio.CassySoundPlayer
import com.azuredevid.cassy.core.coroutines.runCatchingCancellable
import com.azuredevid.cassy.core.hardware.KdsTicketRouter
import com.azuredevid.cassy.core.hardware.PrinterPort9100Manager
import com.azuredevid.cassy.core.security.SessionManager
import com.azuredevid.cassy.data.local.OfflineSyncCoordinator
import com.azuredevid.cassy.data.model.CatalogProduct
import com.azuredevid.cassy.data.network.CassyRealtimeClient
import com.azuredevid.cassy.data.network.RealtimeEventEnvelope
import com.azuredevid.cassy.domain.model.PosOrder
import com.azuredevid.cassy.domain.repository.CatalogRepository
import com.azuredevid.cassy.domain.repository.OrderQuoteRepository
import com.azuredevid.cassy.domain.repository.PosOrderRepository
import com.azuredevid.cassy.domain.repository.PosTable
import com.azuredevid.cassy.domain.repository.PrinterSettingsRepository
import com.azuredevid.cassy.domain.repository.StorePrinterRepository
import com.azuredevid.cassy.domain.repository.TableTabRepository
import com.azuredevid.cassy.domain.repository.toPrinterConfig
import com.azuredevid.cassy.ui.designsystem.CassyToastTone
import com.azuredevid.cassy.ui.designsystem.UiText
import com.azuredevid.cassy.ui.designsystem.toUiText
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
        const val KEY_SEARCH_QUERY = "pos_search_query"
        const val KEY_ORDER_TYPE = "pos_order_type"
        const val KEY_TABLE_NAME = "pos_table_name"
    }

    private val printDispatcher = PosPrintDispatcher(printerManager)
    private val tabCoordinator = PosTabCoordinator(tableTabRepository)
    private val paymentCoordinator = PosPaymentCoordinator(
        posOrderRepository = posOrderRepository,
        printerSettings = printerSettings,
        printerManager = printerManager,
        printDispatcher = printDispatcher,
        tabCoordinator = tabCoordinator,
        soundPlayer = soundPlayer
    )

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
                if (event.type.equals(RealtimeEventEnvelope.TYPE_RESYNC_REQUIRED, ignoreCase = true) ||
                    event.type.startsWith("catalog.", ignoreCase = true)
                ) {
                    loadInitialData()
                    if (_uiState.value.activeQrisPayment != null) {
                        refreshQrisStatus()
                    }
                    return@collect
                }
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
                    refreshQrisStatus()
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
            storePrinters?.listForTablet()?.onSuccess { printerSettings.saveConfig(it.toPrinterConfig()) }
        }

        viewModelScope.launch {
            posOrderRepository.listSalesChannels().onSuccess { channels ->
                _uiState.update { it.copy(salesChannels = channels.filter { ch -> ch.isActive }) }
            }
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

    fun selectSalesChannel(channelId: String?) {
        if (_uiState.value.isCartLocked) return
        _uiState.update { it.copy(selectedSalesChannelId = channelId) }
        refreshQuote()
    }

    fun lookupCustomerByPhone(phone: String) {
        val cleaned = phone.trim()
        if (cleaned.isBlank()) {
            _uiState.update { it.copy(matchedCustomerStamps = null) }
            return
        }
        viewModelScope.launch {
            posOrderRepository.lookupCustomerByPhone(cleaned)
                .onSuccess { stamps ->
                    _uiState.update {
                        it.copy(
                            matchedCustomerStamps = stamps,
                            customerName = stamps.name.ifBlank { it.customerName }
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(matchedCustomerStamps = null) }
                    _effects.emit(PosUiEffect.ShowToast(err.toUiText(R.string.msg_generic_error)))
                }
        }
    }

    fun sendDigitalReceipt(orderId: String, channel: String, recipient: String) {
        if (orderId.isBlank() || recipient.isBlank()) return
        viewModelScope.launch {
            posOrderRepository.sendDigitalReceipt(orderId, channel, recipient.trim())
                .onSuccess { res ->
                    _effects.emit(PosUiEffect.DigitalReceiptSent(res.recipientMasked, res.channel))
                    _effects.emit(
                        PosUiEffect.ShowToast(
                            UiText.res(R.string.pos_msg_digital_receipt_sent, res.recipientMasked),
                            CassyToastTone.SUCCESS
                        )
                    )
                }
                .onFailure { err ->
                    _effects.emit(PosUiEffect.ShowToast(err.toUiText(R.string.msg_generic_error), CassyToastTone.ERROR))
                }
        }
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

    fun loadTables() {
        val repo = tableTabRepository ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingTables = true) }
            val result = repo.listTables()
            _uiState.update { it.copy(isLoadingTables = false, tables = result.getOrDefault(it.tables)) }
            result.onFailure { err -> _effects.emit(PosUiEffect.ShowToast(err.toUiText(R.string.pos_msg_tables_failed))) }
        }
    }

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

    fun applyTabOrder(tab: PosOrder) {
        tabCoordinator.applyTabOrder(_uiState, tab)
        refreshQuote()
    }

    /** Reopens a closed table tab before any split share is paid (`POST /api/v1/pos/tabs/{id}/reopen`). */
    fun reopenClosedTab() {
        val snapshot = _uiState.value
        val tabId = snapshot.activeTabId ?: snapshot.pendingOrder?.id ?: return
        if (!snapshot.canReopenClosedTab) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmittingOrder = true, settlementError = null) }
            tabCoordinator.reopenTab(_uiState, tabId)
                .onSuccess {
                    refreshQuote()
                    _effects.emit(PosUiEffect.ShowToast(UiText.res(R.string.pos_msg_tab_reopened), CassyToastTone.SUCCESS))
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isSubmittingOrder = false) }
                    _effects.emit(PosUiEffect.ShowToast(err.toUiText(R.string.msg_generic_error), CassyToastTone.ERROR))
                }
        }
    }

    fun startSplitBill() {
        val snapshot = _uiState.value
        val quote = snapshot.quote ?: return
        if (snapshot.cart.isEmpty() || snapshot.isSubmittingOrder || snapshot.tableId != null) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmittingOrder = true, settlementError = null) }
            paymentCoordinator.ensureOrderCreated(_uiState, snapshot, quote).fold(
                onSuccess = { order -> _uiState.update { it.copy(isSubmittingOrder = false, splitOrderId = order.id) } },
                onFailure = { err ->
                    val message = err.toUiText(R.string.pos_msg_split_order_failed)
                    _uiState.update { it.copy(isSubmittingOrder = false, settlementError = message) }
                    _effects.emit(PosUiEffect.ShowToast(message, CassyToastTone.ERROR))
                }
            )
        }
    }

    fun closeSplitBill() {
        _uiState.update { it.copy(splitOrderId = null) }
    }

    fun onSplitBillSettled(order: PosOrder) {
        val snapshot = _uiState.value
        val belongsToCart = snapshot.pendingOrder?.id == order.id
        viewModelScope.launch {
            if (belongsToCart) {
                printDispatcher.dispatchReceiptsAndKitchenTickets(
                    state = snapshot,
                    order = order,
                    cartSnapshot = snapshot.cart,
                    tableLabel = printDispatcher.resolveTableLabel(snapshot),
                    paymentLabel = "BAGI TAGIHAN",
                    cashTenderedIdr = null,
                    changeIdr = null,
                    roundingIdr = 0L,
                    config = printerSettings.getConfig(),
                    roundNumber = snapshot.dineInRound
                )
                clearCartAfterPayment(lastOrderId = order.id)
            }
            _uiState.update { it.copy(splitOrderId = null) }
            val orderNo = order.orderNumber.ifBlank { order.id.takeLast(8).uppercase() }
            _effects.emit(PosUiEffect.CashPaymentCompleted(orderNo))
            _effects.emit(PosUiEffect.ShowToast(UiText.res(R.string.pos_msg_split_bill_paid, orderNo), CassyToastTone.SUCCESS))
        }
    }

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
            paymentCoordinator.submitCashPayment(
                stateFlow = _uiState,
                snapshot = snapshot,
                quote = quote,
                tenderedAmount = tenderedAmount,
                drawerId = drawerId,
                onClearCart = { clearCartAfterPayment(lastOrderId = it) },
                onEffect = { _effects.emit(it) }
            )
        }
    }

    fun startQrisPayment(drawerId: String? = null) {
        val snapshot = _uiState.value
        val quote = snapshot.quote ?: return
        if (snapshot.cart.isEmpty() || snapshot.isSubmittingOrder) return
        viewModelScope.launch {
            paymentCoordinator.startQrisPayment(
                stateFlow = _uiState,
                snapshot = snapshot,
                quote = quote,
                drawerId = drawerId,
                onStartPolling = { startQrisPolling() },
                onCancelPolling = {
                    qrisPollJob?.cancel()
                    qrisPollJob = null
                },
                onClearCart = { clearCartAfterPayment(lastOrderId = it) },
                onEffect = { _effects.emit(it) }
            )
        }
    }

    fun refreshQrisStatus() {
        viewModelScope.launch {
            paymentCoordinator.checkQrisStatusOnce(
                stateFlow = _uiState,
                onCancelPolling = {
                    qrisPollJob?.cancel()
                    qrisPollJob = null
                },
                onClearCart = { clearCartAfterPayment(lastOrderId = it) },
                onEffect = { _effects.emit(it) }
            )
        }
    }

    fun dismissQrisModal() {
        qrisPollJob?.cancel()
        qrisPollJob = null
        _uiState.update { it.copy(activeQrisPayment = null, isCheckingQrisStatus = false) }
    }

    private fun startQrisPolling() {
        qrisPollJob?.cancel()
        qrisPollJob = viewModelScope.launch {
            while (isActive && _uiState.value.activeQrisPayment != null) {
                delay(3_000L)
                paymentCoordinator.checkQrisStatusOnce(
                    stateFlow = _uiState,
                    onCancelPolling = {
                        qrisPollJob?.cancel()
                        qrisPollJob = null
                    },
                    onClearCart = { clearCartAfterPayment(lastOrderId = it) },
                    onEffect = { _effects.emit(it) }
                )
            }
        }
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
                tabCoordinator.submitTabRound(_uiState, snapshot).onFailure { err ->
                    _effects.emit(PosUiEffect.ShowToast(err.toUiText(R.string.pos_msg_round_failed)))
                    return@launch
                }
            }
            val config = printerSettings.getConfig()
            val stations = printDispatcher.buildStationConfigs(config)
            if (stations.isEmpty()) {
                _effects.emit(PosUiEffect.ShowToast(UiText.res(R.string.pos_msg_kitchen_printer_missing)))
                return@launch
            }
            val router = KdsTicketRouter(printerManager)
            val result = with(printDispatcher) {
                router.routeOrderDynamic(
                    orderNumber = snapshot.pendingOrder?.orderNumber?.ifBlank { null } ?: "DAPUR-R${snapshot.dineInRound}",
                    tableOrQueue = resolveTableLabel(snapshot),
                    items = snapshot.cart.toStationItems(),
                    stations = stations,
                    cashierPrinterIp = config.cashierPrinterIp,
                    deliveryMode = KdsTicketRouter.OrderDeliveryMode.DINE_IN,
                    roundNumber = snapshot.dineInRound
                )
            }
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
            val stations = printDispatcher.buildStationConfigs(config)
            if (stations.isEmpty()) {
                _effects.emit(PosUiEffect.ShowToast(UiText.res(R.string.pos_msg_kitchen_printer_missing_short)))
                return@launch
            }
            val router = KdsTicketRouter(printerManager)
            val result = with(printDispatcher) {
                router.routeOrderDynamic(
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
            }
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

    fun clearCartAfterPayment(lastOrderId: String? = null) {
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
                tableId = null,
                tableName = if (it.tableId != null) "" else it.tableName,
                splitOrderId = null,
                matchedCustomerStamps = null,
                lastSettledOrderId = lastOrderId ?: it.lastSettledOrderId
            )
        }
    }

    fun setTabSplitCount(count: Int) {
        val state = _uiState.value
        if (state.tableId == null || state.pendingOrder != null || state.isCartLocked) return
        _uiState.update { it.copy(tabSplitCount = count.coerceIn(1, PosTabCoordinator.MAX_TAB_SPLITS)) }
    }

    fun splitTab() {
        val snapshot = _uiState.value
        if (snapshot.tableId == null || snapshot.tabSplitCount < 2 || snapshot.pendingOrder != null ||
            snapshot.cart.isEmpty() || snapshot.isCartLocked
        ) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmittingOrder = true, settlementError = null) }
            tabCoordinator.closeTab(_uiState, snapshot) { repo, tabId ->
                repo.closeTab(tabId, snapshot.tabSplitCount)
            }.onFailure { err ->
                _effects.emit(PosUiEffect.ShowToast(err.toUiText(R.string.pos_msg_split_failed), CassyToastTone.ERROR))
            }
            _uiState.update { it.copy(isSubmittingOrder = false) }
        }
    }

    fun openItemSplit() {
        val state = _uiState.value
        if (!state.canSplitByItem) return
        _uiState.update { it.copy(itemSplit = tabCoordinator.buildItemSplitDraft(state)) }
    }

    fun dismissItemSplit() {
        if (_uiState.value.isSubmittingOrder) return
        _uiState.update { it.copy(itemSplit = null) }
    }

    fun setItemSplitGuests(count: Int) = editItemSplit { draft ->
        tabCoordinator.setItemSplitGuests(draft, count)
    }

    fun assignUnit(index: Int, guest: Int) = editItemSplit { draft ->
        tabCoordinator.assignUnit(draft, index, guest)
    }

    fun confirmItemSplit() {
        val snapshot = _uiState.value
        val draft = snapshot.itemSplit ?: return
        if (!draft.canConfirm || snapshot.isSubmittingOrder) return
        val shares = tabCoordinator.buildGuestItemShares(draft)
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmittingOrder = true, settlementError = null) }
            tabCoordinator.closeTab(_uiState, snapshot) { repo, tabId ->
                repo.closeTabByItem(tabId, shares)
            }
                .onSuccess { _uiState.update { it.copy(itemSplit = null) } }
                .onFailure { err -> _effects.emit(PosUiEffect.ShowToast(err.toUiText(R.string.pos_msg_split_failed), CassyToastTone.ERROR)) }
            _uiState.update { it.copy(isSubmittingOrder = false) }
        }
    }

    private fun editItemSplit(change: (ItemSplitDraft) -> ItemSplitDraft) {
        if (_uiState.value.isSubmittingOrder) return
        _uiState.update { state -> state.itemSplit?.let { state.copy(itemSplit = change(it)) } ?: state }
    }

    fun selectSplit(splitId: String) {
        val state = _uiState.value
        if (state.isCartLocked || state.tabSplits.none { it.id == splitId && !it.isPaid }) return
        _uiState.update { it.copy(pendingSplitId = splitId) }
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
