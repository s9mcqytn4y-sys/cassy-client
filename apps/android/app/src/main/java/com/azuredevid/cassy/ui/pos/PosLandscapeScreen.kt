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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.azuredevid.cassy.R
import com.azuredevid.cassy.data.model.CatalogProduct
import com.azuredevid.cassy.data.model.OrderQuoteItem
import com.azuredevid.cassy.data.model.OrderQuoteRequest
import com.azuredevid.cassy.ui.designsystem.CassySnackbarHost
import com.azuredevid.cassy.ui.designsystem.CassyToastTone
import com.azuredevid.cassy.ui.designsystem.showCassySnackbar
import com.azuredevid.cassy.ui.pos.components.PosCashModal
import com.azuredevid.cassy.ui.pos.components.PosCatalogGrid
import com.azuredevid.cassy.ui.pos.components.PosNavRail
import com.azuredevid.cassy.ui.pos.components.PosOrderSummaryPanel
import com.azuredevid.cassy.ui.pos.components.PosQrisModal
import com.azuredevid.cassy.ui.pos.split.SplitSettlementEffect
import com.azuredevid.cassy.ui.pos.split.SplitSettlementViewModel
import com.azuredevid.cassy.ui.pos.split.components.SplitSettlementDialog
import com.azuredevid.cassy.ui.shift.ShiftUiEffect
import com.azuredevid.cassy.ui.shift.ShiftViewModel
import com.azuredevid.cassy.ui.shift.components.CashMovementModal
import com.azuredevid.cassy.ui.shift.components.CloseShiftModal
import com.azuredevid.cassy.ui.shift.components.OpenShiftModal
import com.azuredevid.cassy.ui.shift.components.ShiftStatusBanner
import com.azuredevid.cassy.ui.shift.components.XReportModal
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import java.text.NumberFormat
import java.util.Locale

@Immutable
data class CartItem(
    val product: CatalogProduct,
    val quantity: Int = 1,
    val notes: String = "",
    val isSentToKitchen: Boolean = false
)

/**
 * Modular 10%-55%-35% landscape POS screen for tablets with unified Cassy snackbar feedback.
 */
@Composable
fun PosLandscapeScreen(
    posViewModel: PosViewModel = koinViewModel(),
    shiftViewModel: ShiftViewModel = koinViewModel(),
    splitViewModel: SplitSettlementViewModel = koinViewModel(),
    onOpenDrawerKick: () -> Unit = {},
    onNavigateToSettings: () -> Unit,
    onNavigateToCatalog: () -> Unit = {},
    onNavigateToKds: () -> Unit = {},
    onNavigateToTables: () -> Unit = {},
    onNavigateToPairing: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val rupiahFormat = remember { NumberFormat.getIntegerInstance(Locale("id", "ID")) }

    val posState by posViewModel.uiState.collectAsStateWithLifecycle()
    val shiftState by shiftViewModel.uiState.collectAsStateWithLifecycle()
    val splitState by splitViewModel.uiState.collectAsStateWithLifecycle()

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
                        message = effect.message,
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
                        message = effect.message,
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
                            displayedProducts = posState.displayedProducts,
                            searchQuery = posState.searchQuery,
                            onSearchQueryChange = { posViewModel.updateSearchQuery(it) },
                            isLoadingCatalog = posState.isLoadingCatalog,
                            catalogError = posState.catalogError,
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
                        onDecreaseQuantity = { item -> posViewModel.decreaseItemQuantity(item.product.id) },
                        quote = posState.quote,
                        quoteError = posState.quoteError,
                        rupiahFormat = rupiahFormat,
                        onOpenDrawerKick = {
                            if (posState.isPrinterConfigured) {
                                posViewModel.kickDrawerManual()
                            } else {
                                onOpenDrawerKick()
                            }
                        },
                        onSendToKitchen = {
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
                        },
                        onOpenCashModal = {
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
                        },
                        onOpenQrisModal = {
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
                        },
                        onOpenSplitBill = {
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
                        },
                        onOpenOpenBills = { splitViewModel.showOpenBills() },
                        failoverWarning = posState.failoverAlert,
                        onReprintStations = { posViewModel.reprintStations() }
                    )
                }
            }

            val pricedQuote = posState.quote
            if (posState.showCashModal && pricedQuote != null) {
                PosCashModal(
                    grandTotal = pricedQuote.totalAmount,
                    rupiahFormat = rupiahFormat,
                    isSubmitting = posState.isSubmittingOrder,
                    errorMessage = posState.settlementError,
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

            SplitSettlementDialog(
                state = splitState,
                rupiahFormat = rupiahFormat,
                onDivide = { plan -> splitViewModel.divide(plan) },
                onPayCash = { shareId -> splitViewModel.startCashForShare(shareId) },
                onPayQris = { shareId -> splitViewModel.payShareQris(shareId, shiftState.currentShift?.drawerId) },
                onOpenBill = { orderId -> splitViewModel.open(orderId) },
                onDismiss = {
                    splitViewModel.close()
                    posViewModel.closeSplitBill()
                }
            )

            val cashShare = splitState.cashShareId?.let { id -> splitState.bill?.shares?.firstOrNull { it.id == id } }
            if (cashShare != null) {
                PosCashModal(
                    grandTotal = cashShare.remainingAmount,
                    rupiahFormat = rupiahFormat,
                    isSubmitting = splitState.payingShareId == cashShare.id,
                    onDismiss = { splitViewModel.cancelCash() },
                    onConfirmCashPayment = { tenderedLong, _ ->
                        splitViewModel.payShareCash(cashShare.id, tenderedLong, shiftState.currentShift?.drawerId)
                    }
                )
            }

            val splitQris = splitState.activeQris
            if (splitQris != null) {
                PosQrisModal(
                    orderNumber = splitState.bill?.order?.orderNumber.orEmpty(),
                    payment = splitQris,
                    rupiahFormat = rupiahFormat,
                    isCheckingStatus = splitState.isBusy,
                    onCheckStatus = { splitViewModel.checkQrisStatus() },
                    onDismiss = { splitViewModel.dismissQris() }
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
                    errorMessage = shiftState.errorMessage,
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
                    errorMessage = shiftState.errorMessage,
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
                    errorMessage = shiftState.errorMessage,
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
