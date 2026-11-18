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
import androidx.compose.ui.Alignment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.azuredevid.cassy.ui.pos.components.PosCashModal
import com.azuredevid.cassy.ui.pos.components.PosCatalogGrid
import com.azuredevid.cassy.ui.pos.components.PosMobilePortraitLayout
import com.azuredevid.cassy.ui.pos.components.PosNavRail
import com.azuredevid.cassy.ui.pos.components.PosOrderSummaryPanel
import com.azuredevid.cassy.ui.pos.components.PosQrisModal
import com.azuredevid.cassy.ui.shift.ShiftUiEffect
import com.azuredevid.cassy.ui.shift.ShiftViewModel
import com.azuredevid.cassy.ui.shift.components.CashMovementModal
import com.azuredevid.cassy.ui.shift.components.CloseShiftModal
import com.azuredevid.cassy.ui.shift.components.OpenShiftModal
import com.azuredevid.cassy.ui.shift.components.ShiftStatusBanner
import com.azuredevid.cassy.ui.shift.components.XReportModal
import kotlinx.coroutines.launch
import com.azuredevid.cassy.ui.pos.components.PosServiceRequestPill
import com.azuredevid.cassy.ui.pos.components.PosServiceRequestsModal
import com.azuredevid.cassy.ui.pos.components.PosTabItemVoidDialog
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
    onOpenDrawerKick: () -> Unit = {},
    onNavigateToSettings: () -> Unit,
    onNavigateToCatalog: () -> Unit = {},
    onNavigateToPairing: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val rupiahFormat = remember { NumberFormat.getIntegerInstance(Locale("id", "ID")) }

    val posState by posViewModel.uiState.collectAsStateWithLifecycle()
    val shiftState by shiftViewModel.uiState.collectAsStateWithLifecycle()
    val serviceState by serviceRequestViewModel.uiState.collectAsStateWithLifecycle()
    val voidState by tabItemVoidViewModel.uiState.collectAsStateWithLifecycle()

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

                if (totalWidth >= CassyDimens.SplitPaneBreakpoint) {
                    val navWidth = totalWidth * 0.12f
                    val catalogWidth = totalWidth * 0.53f
                    val orderWidth = totalWidth * 0.35f

                    Row(modifier = Modifier.fillMaxSize()) {
                        PosNavRail(
                            railWidth = navWidth,
                            categories = categories,
                            selectedCategoryId = posState.selectedCategoryId,
                            onCategorySelected = { posViewModel.selectCategory(it) },
                            allProducts = posState.allProducts,
                            isPrinterOnline = posState.isPrinterConfigured,
                            onNavigateToSettings = onNavigateToSettings,
                            onNavigateToCatalog = onNavigateToCatalog,
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
                            onTableSelected = { posViewModel.selectTable(it) }
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
                        onNavigateToSettings = onNavigateToSettings,
                        onNavigateToCatalog = onNavigateToCatalog,
                        onNavigateToPairing = onNavigateToPairing
                    )
                }
            }

            val pricedQuote = posState.quote
            if (posState.showCashModal && pricedQuote != null) {
                PosCashModal(
                    grandTotal = pricedQuote.totalAmount,
                    rupiahFormat = rupiahFormat,
                    isSubmitting = posState.isSubmittingOrder,
                    errorMessage = posState.settlementError?.asString(),
                    onDismiss = { posViewModel.closeCashModal() },
                    onConfirmCashPayment = { tenderedLong, _ ->
                        posViewModel.submitCashPayment(
                            tenderedAmount = tenderedLong,
                            drawerId = shiftState.currentShift?.drawerId
                        )
                    }
                )
            }

            val activeQris = posState.activeQrisPayment
            if (activeQris != null) {
                PosQrisModal(
                    orderNumber = posState.pendingOrder?.orderNumber.orEmpty(),
                    payment = activeQris,
                    rupiahFormat = rupiahFormat,
                    isCheckingStatus = posState.isCheckingQrisStatus,
                    onCheckStatus = { posViewModel.refreshQrisStatus() },
                    onDismiss = { posViewModel.dismissQrisModal() }
                )
            }

            if (serviceState.requests.isNotEmpty()) {
                PosServiceRequestPill(
                    count = serviceState.requests.size,
                    onClick = { serviceRequestViewModel.openPanel() },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = CassyDimens.SpaceSm)
                )
            }
            if (voidState.isOpen) {
                PosTabItemVoidDialog(
                    state = voidState,
                    onQuantityChange = tabItemVoidViewModel::setQuantity,
                    onReasonChange = tabItemVoidViewModel::setReason,
                    onPinChange = tabItemVoidViewModel::setPin,
                    onOwnerPinChange = tabItemVoidViewModel::setOwnerPin,
                    onSubmit = tabItemVoidViewModel::submit,
                    onAskOwner = tabItemVoidViewModel::requestOwnerApproval,
                    onDismiss = tabItemVoidViewModel::dismiss
                )
            }
            if (serviceState.isPanelOpen) {
                PosServiceRequestsModal(
                    requests = serviceState.requests,
                    busyRequestId = serviceState.busyRequestId,
                    onAcknowledge = { serviceRequestViewModel.acknowledge(it) },
                    onDone = { serviceRequestViewModel.markDone(it) },
                    onDismiss = { serviceRequestViewModel.closePanel() }
                )
            }

            if (shiftState.showOpenShiftModal) {
                OpenShiftModal(
                    drawers = shiftState.drawers,
                    selectedDrawerId = shiftState.selectedDrawerId,
                    onDrawerSelected = { shiftViewModel.onDrawerSelected(it) },
                    openingFloatInput = shiftState.openingFloatInput,
                    notes = shiftState.openShiftNotes,
                    isOpening = shiftState.isOpeningShift,
                    errorMessage = shiftState.errorMessage?.asString(),
                    onOpeningFloatChanged = { shiftViewModel.onOpeningFloatChanged(it) },
                    onNotesChanged = { shiftViewModel.onOpenShiftNotesChanged(it) },
                    onSubmit = { shiftViewModel.submitOpenShift() },
                    onDismiss = { shiftViewModel.closeOpenShiftModal() }
                )
            }

            if (shiftState.showCashMovementModal) {
                CashMovementModal(
                    movementType = shiftState.movementType,
                    amountInput = shiftState.movementAmountInput,
                    reason = shiftState.movementReason,
                    isRecording = shiftState.isRecordingMovement,
                    errorMessage = shiftState.errorMessage?.asString(),
                    onMovementTypeChanged = { shiftViewModel.onMovementTypeChanged(it) },
                    onAmountChanged = { shiftViewModel.onMovementAmountChanged(it) },
                    onReasonChanged = { shiftViewModel.onMovementReasonChanged(it) },
                    onSubmit = { shiftViewModel.submitCashMovement() },
                    onDismiss = { shiftViewModel.closeCashMovementModal() }
                )
            }

            if (shiftState.showXReportModal) {
                XReportModal(
                    report = shiftState.currentXReport,
                    isLoading = shiftState.isLoadingXReport,
                    isPrinting = shiftState.isPrintingReport,
                    onPrint = { shiftViewModel.printCurrentXReport() },
                    onDismiss = { shiftViewModel.closeXReportModal() }
                )
            }

            if (shiftState.showCloseShiftModal) {
                CloseShiftModal(
                    currentShift = shiftState.currentShift,
                    countedCashInput = shiftState.countedCashInput,
                    varianceReason = shiftState.varianceReason,
                    notes = shiftState.closeShiftNotes,
                    supervisorPinInput = shiftState.supervisorPinInput,
                    calculatedVariance = shiftState.calculatedVariance,
                    isSupervisorAuthRequired = shiftState.isSupervisorAuthRequired,
                    printReceiptOnClose = shiftState.printReceiptOnClose,
                    isClosing = shiftState.isClosingShift,
                    errorMessage = shiftState.errorMessage?.asString(),
                    onCountedCashChanged = { shiftViewModel.onCountedCashChanged(it) },
                    onVarianceReasonChanged = { shiftViewModel.onVarianceReasonChanged(it) },
                    onNotesChanged = { shiftViewModel.onCloseShiftNotesChanged(it) },
                    onSupervisorPinChanged = { shiftViewModel.onSupervisorPinChanged(it) },
                    onPrintReceiptToggled = { shiftViewModel.onPrintReceiptOnCloseToggled(it) },
                    onSubmit = { shiftViewModel.submitCloseShift() },
                    onDismiss = { shiftViewModel.closeCloseShiftModal() }
                )
            }
        }
    }
}

/** Request body for the server quote; order types follow the API (dine_in, takeaway). */
internal fun quoteRequestOf(cart: List<CartItem>, orderType: String) = OrderQuoteRequest(
    orderType = if (orderType == "DINE-IN") "dine_in" else "takeaway",
    items = cart.map { OrderQuoteItem(productId = it.product.id, quantity = it.quantity) }
)
