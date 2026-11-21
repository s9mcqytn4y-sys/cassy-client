package com.azuredevid.cassy.ui.pos

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.azuredevid.cassy.R
import com.azuredevid.cassy.data.model.CatalogProduct
import com.azuredevid.cassy.data.model.OrderQuoteItem
import com.azuredevid.cassy.data.model.OrderQuoteRequest
import com.azuredevid.cassy.domain.model.PosOrderItem
import com.azuredevid.cassy.ui.designsystem.CassyDimens
import com.azuredevid.cassy.ui.designsystem.CassySnackbarHost
import com.azuredevid.cassy.ui.designsystem.CassyToastTone
import com.azuredevid.cassy.ui.designsystem.showCassySnackbar
import com.azuredevid.cassy.ui.pos.components.PosCatalogGrid
import com.azuredevid.cassy.ui.pos.components.PosMobilePortraitLayout
import com.azuredevid.cassy.ui.pos.components.PosNavRail
import com.azuredevid.cassy.ui.pos.components.PosOrderSummaryPanel
import com.azuredevid.cassy.ui.pos.components.PosTabSplitSection
import com.azuredevid.cassy.ui.pos.split.SplitSettlementEffect
import com.azuredevid.cassy.ui.pos.split.SplitSettlementViewModel
import com.azuredevid.cassy.ui.shift.ShiftUiEffect
import com.azuredevid.cassy.ui.shift.ShiftViewModel
import com.azuredevid.cassy.ui.shift.components.ShiftStatusBanner
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import java.text.NumberFormat
import java.util.Locale

@Immutable
data class CartItem(
    val product: CatalogProduct,
    val quantity: Int = 1,
    val notes: String = "",
    val isSentToKitchen: Boolean = false,
    /** Server lines of an open tab behind this sent row; a void targets one of them by id. */
    val sentLines: List<PosOrderItem> = emptyList()
)

/**
 * Modular 10%-55%-35% landscape POS screen for tablets with unified Cassy snackbar feedback.
 */
@Composable
fun PosLandscapeScreen(
    posViewModel: PosViewModel = koinViewModel(),
    shiftViewModel: ShiftViewModel = koinViewModel(),
    serviceRequestViewModel: ServiceRequestViewModel = koinViewModel(),
    tabItemVoidViewModel: TabItemVoidViewModel = koinViewModel(),
    splitViewModel: SplitSettlementViewModel = koinViewModel(),
    onOpenDrawerKick: () -> Unit = {},
    onNavigateToSettings: () -> Unit,
    onNavigateToCatalog: () -> Unit = {},
    onNavigateToKds: () -> Unit = {},
    onNavigateToTables: () -> Unit = {},
    onNavigateToPairing: () -> Unit = {},
    splitBillEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val rupiahFormat = remember { NumberFormat.getIntegerInstance(Locale.forLanguageTag("id-ID")) }
    var showDigitalReceiptModal by rememberSaveable { mutableStateOf(false) }

    val posState by posViewModel.uiState.collectAsStateWithLifecycle()
    val shiftState by shiftViewModel.uiState.collectAsStateWithLifecycle()
    val serviceState by serviceRequestViewModel.uiState.collectAsStateWithLifecycle()
    val voidState by tabItemVoidViewModel.uiState.collectAsStateWithLifecycle()
    val splitState by splitViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(tabItemVoidViewModel) {
        tabItemVoidViewModel.voided.collect { result ->
            posViewModel.applyTabOrder(result.tab)
            snackbarHostState.showCassySnackbar(
                message = context.getString(R.string.tab_void_msg_done, result.itemName),
                tone = CassyToastTone.SUCCESS
            )
        }
    }

    // A sent row of an open tab is voided line by line from its newest round; unsent rows just shrink.
    val handleDecrease: (CartItem) -> Unit = { item ->
        val tabId = posState.activeTabId
        val line = item.sentLines.lastOrNull()
        if (!item.isSentToKitchen) {
            posViewModel.decreaseItemQuantity(item.product.id)
        } else if (tabId != null && line != null && posState.pendingSplitId == null) {
            tabItemVoidViewModel.open(tabId, line, item.product.name)
        }
    }

    LaunchedEffect(serviceRequestViewModel) {
        serviceRequestViewModel.errors.collect { message ->
            snackbarHostState.showCassySnackbar(message = message.asString(context), tone = CassyToastTone.ERROR)
        }
    }

    LaunchedEffect(posState.splitOrderId) {
        posState.splitOrderId?.let { splitViewModel.open(it) }
    }

    LaunchedEffect(splitViewModel) {
        splitViewModel.effects.collect { effect ->
            when (effect) {
                is SplitSettlementEffect.OpenDrawer -> posViewModel.kickDrawerForSplitCash()
                is SplitSettlementEffect.BillSettled -> {
                    splitViewModel.close()
                    posViewModel.onSplitBillSettled(effect.order)
                }
            }
        }
    }

    LaunchedEffect(shiftViewModel) {
        shiftViewModel.effects.collect { effect ->
            when (effect) {
                is ShiftUiEffect.ShowToast -> {
                    snackbarHostState.showCassySnackbar(
                        message = effect.message.asString(context),
                        tone = effect.tone
                    )
                }
                is ShiftUiEffect.PromptOpenShift -> shiftViewModel.openShiftModal()
                is ShiftUiEffect.ShiftOpenedSuccessfully -> Unit
                is ShiftUiEffect.ShiftClosedSuccessfully -> Unit
            }
        }
    }

    LaunchedEffect(posViewModel) {
        posViewModel.effects.collect { effect ->
            when (effect) {
                is PosUiEffect.ShowToast -> {
                    snackbarHostState.showCassySnackbar(
                        message = effect.message.asString(context),
                        tone = effect.tone
                    )
                }
                is PosUiEffect.CashPaymentCompleted -> shiftViewModel.loadCurrentShift()
                is PosUiEffect.QrisPaymentCompleted -> shiftViewModel.loadCurrentShift()
                is PosUiEffect.DigitalReceiptSent -> Unit
            }
        }
    }

    val categories: List<Pair<String, String>> = remember(posState.categories, context) {
        listOf("all" to context.getString(R.string.category_all)) +
            posState.categories.map { it.id to it.name }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { CassySnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val totalWidth = maxWidth
                val handleOpenDrawerKick: () -> Unit = {
                    if (posState.isPrinterConfigured) {
                        posViewModel.kickDrawerManual()
                    } else {
                        onOpenDrawerKick()
                    }
                }
                val handleSendToKitchen: () -> Unit = {
                    if (!shiftState.isShiftOpen) {
                        scope.launch {
                            snackbarHostState.showCassySnackbar(
                                message = context.getString(R.string.shift_required_for_kitchen),
                                tone = CassyToastTone.WARNING
                            )
                        }
                        shiftViewModel.openShiftModal()
                    } else {
                        posViewModel.sendToKitchen()
                    }
                }
                val splitSection: @Composable () -> Unit = {
                    // With split_bill off a tab that was already split stays payable per share.
                    val canShow = splitBillEnabled || posState.tabSplits.size > 1
                    if (posState.tableId != null && posState.cart.isNotEmpty() && canShow) {
                        PosTabSplitSection(
                            splitCount = posState.tabSplitCount,
                            splits = posState.tabSplits,
                            selectedSplitId = posState.pendingSplitId,
                            // A bill closed as one share can no longer be divided here.
                            isBusy = posState.isCartLocked || (posState.pendingOrder != null && posState.tabSplits.size < 2),
                            rupiahFormat = rupiahFormat,
                            onSplitCountChange = posViewModel::setTabSplitCount,
                            onSplit = posViewModel::splitTab,
                            onSelectSplit = posViewModel::selectSplit,
                            canSplitByItem = posState.canSplitByItem,
                            onSplitByItem = posViewModel::openItemSplit
                        )
                    }
                }
                val handleOpenCashModal: () -> Unit = {
                    if (!shiftState.isShiftOpen) {
                        scope.launch {
                            snackbarHostState.showCassySnackbar(
                                message = context.getString(R.string.shift_required_for_payment),
                                tone = CassyToastTone.WARNING
                            )
                        }
                        shiftViewModel.openShiftModal()
                    } else {
                        posViewModel.openCashModal()
                    }
                }
                val handleOpenQrisModal: () -> Unit = {
                    if (!shiftState.isShiftOpen) {
                        scope.launch {
                            snackbarHostState.showCassySnackbar(
                                message = context.getString(R.string.shift_required_for_payment),
                                tone = CassyToastTone.WARNING
                            )
                        }
                        shiftViewModel.openShiftModal()
                    } else {
                        posViewModel.startQrisPayment(shiftState.currentShift?.drawerId)
                    }
                }
                val handleOpenSplitBill: () -> Unit = {
                    if (!shiftState.isShiftOpen) {
                        scope.launch {
                            snackbarHostState.showCassySnackbar(
                                message = context.getString(R.string.shift_required_for_payment),
                                tone = CassyToastTone.WARNING
                            )
                        }
                        shiftViewModel.openShiftModal()
                    } else {
                        posViewModel.startSplitBill()
                    }
                }

                if (totalWidth >= CassyDimens.PosThreeColumnBreakpoint) {
                    val catalogWidth = totalWidth - CassyDimens.PosRailWidth - CassyDimens.PosOrderPanelWidth - 2.dp
                    val orderWidth = CassyDimens.PosOrderPanelWidth

                    Row(modifier = Modifier.fillMaxSize()) {
                        PosNavRail(
                            isPrinterOnline = posState.isPrinterConfigured,
                            onNavigateToSettings = onNavigateToSettings,
                            onNavigateToCatalog = onNavigateToCatalog,
                            onNavigateToKds = onNavigateToKds,
                            onNavigateToTables = onNavigateToTables,
                            onNavigateToPairing = onNavigateToPairing
                        )

                        VerticalDivider(color = MaterialTheme.colorScheme.outline)

                        Column(
                            modifier = Modifier
                                .width(catalogWidth)
                                .fillMaxHeight()
                        ) {
                            ShiftStatusBanner(
                                isShiftOpen = shiftState.isShiftOpen,
                                currentShift = shiftState.currentShift,
                                onOpenShiftClick = { shiftViewModel.openShiftModal() },
                                onXReportClick = { shiftViewModel.openXReportModal() },
                                onCashMovementClick = { shiftViewModel.openCashMovementModal() },
                                onCloseShiftClick = { shiftViewModel.openCloseShiftModal() }
                            )

                            PosCatalogGrid(
                                gridWidth = catalogWidth,
                                categories = categories,
                                selectedCategoryId = posState.selectedCategoryId,
                                onCategorySelected = { posViewModel.selectCategory(it) },
                                displayedProducts = posState.displayedProducts,
                                searchQuery = posState.searchQuery,
                                onSearchQueryChange = { posViewModel.updateSearchQuery(it) },
                                isLoadingCatalog = posState.isLoadingCatalog,
                                catalogError = posState.catalogError?.asString(),
                                onRetryCatalog = { posViewModel.loadInitialData() },
                                onProductSelected = { product -> posViewModel.addProductToCart(product) },
                                rupiahFormat = rupiahFormat,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        VerticalDivider(color = MaterialTheme.colorScheme.outline)

                        PosOrderSummaryPanel(
                            panelWidth = orderWidth,
                            tableName = posState.tableName,
                            orderType = posState.orderType,
                            onTableNameChanged = { posViewModel.updateTableName(it) },
                            onToggleOrderType = { posViewModel.toggleOrderType() },
                            cart = posState.cart,
                            isCartLocked = posState.isCartLocked,
                            isShiftOpen = shiftState.isShiftOpen,
                            isSubmittingOrder = posState.isSubmittingOrder,
                            onIncreaseQuantity = { item -> posViewModel.increaseItemQuantity(item.product.id) },
                            onDecreaseQuantity = handleDecrease,
                            quote = posState.quote,
                            quoteError = posState.quoteError?.asString(),
                            rupiahFormat = rupiahFormat,
                            onOpenDrawerKick = handleOpenDrawerKick,
                            onSendToKitchen = handleSendToKitchen,
                            onOpenCashModal = handleOpenCashModal,
                            onOpenQrisModal = handleOpenQrisModal,
                            failoverWarning = posState.failoverAlert?.asString(),
                            onReprintStations = { posViewModel.reprintStations() },
                            tables = posState.tables,
                            isLoadingTables = posState.isLoadingTables,
                            selectedTableId = posState.tableId,
                            onOpenTableBoard = { posViewModel.loadTables() },
                            onTableSelected = { posViewModel.selectTable(it) },
                            splitSection = splitSection,
                            splitBillEnabled = splitBillEnabled,
                            canReopenClosedTab = posState.canReopenClosedTab,
                            hasLastSettledOrder = !posState.lastSettledOrderId.isNullOrBlank(),
                            onReopenClosedTab = { posViewModel.reopenClosedTab() },
                            onOpenDigitalReceipt = { showDigitalReceiptModal = true },
                            onOpenSplitBill = handleOpenSplitBill,
                            onOpenOpenBills = { splitViewModel.showOpenBills() }
                        )
                    }
                } else {
                    PosMobilePortraitLayout(
                        totalWidth = totalWidth,
                        categories = categories,
                        selectedCategoryId = posState.selectedCategoryId,
                        onCategorySelected = { posViewModel.selectCategory(it) },
                        displayedProducts = posState.displayedProducts,
                        searchQuery = posState.searchQuery,
                        onSearchQueryChange = { posViewModel.updateSearchQuery(it) },
                        isLoadingCatalog = posState.isLoadingCatalog,
                        catalogError = posState.catalogError?.asString(),
                        onRetryCatalog = { posViewModel.loadInitialData() },
                        onProductSelected = { product -> posViewModel.addProductToCart(product) },
                        isPrinterOnline = posState.isPrinterConfigured,
                        isShiftOpen = shiftState.isShiftOpen,
                        currentShift = shiftState.currentShift,
                        onOpenShiftClick = { shiftViewModel.openShiftModal() },
                        onXReportClick = { shiftViewModel.openXReportModal() },
                        onCashMovementClick = { shiftViewModel.openCashMovementModal() },
                        onCloseShiftClick = { shiftViewModel.openCloseShiftModal() },
                        tableName = posState.tableName,
                        orderType = posState.orderType,
                        onTableNameChanged = { posViewModel.updateTableName(it) },
                        onToggleOrderType = { posViewModel.toggleOrderType() },
                        cart = posState.cart,
                        isCartLocked = posState.isCartLocked,
                        isSubmittingOrder = posState.isSubmittingOrder,
                        onIncreaseQuantity = { item -> posViewModel.increaseItemQuantity(item.product.id) },
                        onDecreaseQuantity = handleDecrease,
                        quote = posState.quote,
                        quoteError = posState.quoteError?.asString(),
                        rupiahFormat = rupiahFormat,
                        onOpenDrawerKick = handleOpenDrawerKick,
                        onSendToKitchen = handleSendToKitchen,
                        onOpenCashModal = handleOpenCashModal,
                        onOpenQrisModal = handleOpenQrisModal,
                        failoverWarning = posState.failoverAlert?.asString(),
                        onReprintStations = { posViewModel.reprintStations() },
                        tables = posState.tables,
                        isLoadingTables = posState.isLoadingTables,
                        selectedTableId = posState.tableId,
                        onOpenTableBoard = { posViewModel.loadTables() },
                        onTableSelected = { posViewModel.selectTable(it) },
                        splitSection = splitSection,
                        onOpenSplitBill = handleOpenSplitBill,
                        onOpenOpenBills = { splitViewModel.showOpenBills() },
                        onNavigateToSettings = onNavigateToSettings,
                        onNavigateToCatalog = onNavigateToCatalog,
                        onNavigateToPairing = onNavigateToPairing,
                        onNavigateToKds = onNavigateToKds,
                        onNavigateToTables = onNavigateToTables,
                        splitBillEnabled = splitBillEnabled,
                        canReopenClosedTab = posState.canReopenClosedTab,
                        hasLastSettledOrder = !posState.lastSettledOrderId.isNullOrBlank(),
                        onReopenClosedTab = { posViewModel.reopenClosedTab() },
                        onOpenDigitalReceipt = { showDigitalReceiptModal = true }
                    )
                }
            }

            PosLandscapeModals(
                posState = posState,
                shiftState = shiftState,
                serviceState = serviceState,
                voidState = voidState,
                splitState = splitState,
                posViewModel = posViewModel,
                shiftViewModel = shiftViewModel,
                serviceRequestViewModel = serviceRequestViewModel,
                tabItemVoidViewModel = tabItemVoidViewModel,
                splitViewModel = splitViewModel,
                rupiahFormat = rupiahFormat,
                showDigitalReceiptModal = showDigitalReceiptModal,
                onDismissDigitalReceiptModal = { showDigitalReceiptModal = false }
            )
        }
    }
}

/** Request body for the server quote; order types follow the API (dine_in, takeaway). */
internal fun quoteRequestOf(cart: List<CartItem>, orderType: String) = OrderQuoteRequest(
    orderType = if (orderType == "DINE-IN") "dine_in" else "takeaway",
    items = cart.map { OrderQuoteItem(productId = it.product.id, quantity = it.quantity) }
)
