package com.azuredevid.cassy.ui.pos.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SoupKitchen
import androidx.compose.material.icons.rounded.TableRestaurant
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.azuredevid.cassy.R
import com.azuredevid.cassy.domain.repository.PosTable
import com.azuredevid.cassy.data.model.CatalogProduct
import com.azuredevid.cassy.data.model.OrderQuote
import com.azuredevid.cassy.domain.model.Shift
import com.azuredevid.cassy.ui.designsystem.CassyDimens
import com.azuredevid.cassy.ui.designsystem.CassyPrimaryButton
import com.azuredevid.cassy.ui.designsystem.CassySecondaryButton
import com.azuredevid.cassy.ui.designsystem.CassyStatusBadge
import com.azuredevid.cassy.ui.designsystem.CassyToastTone
import com.azuredevid.cassy.ui.pos.CartItem
import com.azuredevid.cassy.ui.shift.components.ShiftStatusBanner
import java.text.NumberFormat

/**
 * Adaptive single-column POS layout for compact phones (< 720.dp portrait) with top operational bar,
 * horizontal category chips, 2-column catalog grid, sticky bottom cart bar + quick checkout actions,
 * and a ModalBottomSheet hosting [PosOrderSummaryPanel].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PosMobilePortraitLayout(
    totalWidth: Dp,
    categories: List<Pair<String, String>>,
    selectedCategoryId: String,
    onCategorySelected: (String) -> Unit,
    displayedProducts: List<CatalogProduct>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    isLoadingCatalog: Boolean,
    catalogError: String?,
    onRetryCatalog: () -> Unit,
    onProductSelected: (CatalogProduct) -> Unit,
    isPrinterOnline: Boolean,
    isShiftOpen: Boolean,
    currentShift: Shift?,
    onOpenShiftClick: () -> Unit,
    onXReportClick: () -> Unit,
    onCashMovementClick: () -> Unit,
    onCloseShiftClick: () -> Unit,
    tableName: String,
    orderType: String,
    onTableNameChanged: (String) -> Unit,
    onToggleOrderType: () -> Unit,
    cart: List<CartItem>,
    isCartLocked: Boolean,
    isSubmittingOrder: Boolean,
    onIncreaseQuantity: (CartItem) -> Unit,
    onDecreaseQuantity: (CartItem) -> Unit,
    quote: OrderQuote?,
    isOffline: Boolean = false,
    quoteError: String?,
    rupiahFormat: NumberFormat,
    onOpenDrawerKick: () -> Unit,
    onSendToKitchen: () -> Unit,
    onOpenCashModal: () -> Unit,
    onOpenQrisModal: () -> Unit,
    onOpenEdcModal: () -> Unit,
    failoverWarning: String?,
    onReprintStations: () -> Unit,
    tables: List<PosTable> = emptyList(),
    isLoadingTables: Boolean = false,
    selectedTableId: String? = null,
    onOpenTableBoard: () -> Unit = {},
    onTableSelected: (PosTable) -> Unit = {},
    splitSection: @Composable () -> Unit = {},
    onOpenSplitBill: () -> Unit = {},
    onOpenOpenBills: () -> Unit = {},
    onNavigateToSettings: () -> Unit,
    onNavigateToCatalog: () -> Unit,
    onNavigateToPairing: () -> Unit,
    onNavigateToKds: () -> Unit = {},
    onNavigateToTables: () -> Unit = {},
    splitBillEnabled: Boolean = true,
    canReopenClosedTab: Boolean = false,
    hasLastSettledOrder: Boolean = false,
    onReopenClosedTab: () -> Unit = {},
    onOpenDigitalReceipt: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showMoreMenu by rememberSaveable { mutableStateOf(false) }
    var showCartSheet by rememberSaveable { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val totalItems = cart.sumOf { it.quantity }
    val totalAmountFormatted = rupiahFormat.format(quote?.totalAmount ?: 0L)

    Column(modifier = modifier.fillMaxSize()) {
        // Compact Mobile Top Header
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CassyDimens.SpaceMd, vertical = CassyDimens.SpaceXs),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceSm)
                ) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    CassyStatusBadge(
                        text = stringResource(
                            if (isPrinterOnline) R.string.lan_status_ok else R.string.lan_status_off
                        ),
                        tone = if (isPrinterOnline) CassyToastTone.SUCCESS else CassyToastTone.WARNING
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onNavigateToCatalog,
                        modifier = Modifier.defaultMinSize(
                            minWidth = CassyDimens.TouchTargetMin,
                            minHeight = CassyDimens.TouchTargetMin
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Inventory2,
                            contentDescription = stringResource(R.string.catalog_nav_label)
                        )
                    }
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.defaultMinSize(
                            minWidth = CassyDimens.TouchTargetMin,
                            minHeight = CassyDimens.TouchTargetMin
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = stringResource(R.string.btn_byok_settings)
                        )
                    }
                    // The phone header has no room for the tablet rail, so the less used screens sit in a menu.
                    Box {
                        IconButton(
                            onClick = { showMoreMenu = true },
                            modifier = Modifier.defaultMinSize(
                                minWidth = CassyDimens.TouchTargetMin,
                                minHeight = CassyDimens.TouchTargetMin
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = stringResource(R.string.pos_more_menu)
                            )
                        }
                        DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }) {
                            listOf(
                                Triple(R.string.tables_nav_label, Icons.Rounded.TableRestaurant, onNavigateToTables),
                                Triple(R.string.kds_nav_label, Icons.Rounded.SoupKitchen, onNavigateToKds),
                                Triple(R.string.btn_pair_device, Icons.Rounded.QrCodeScanner, onNavigateToPairing)
                            ).forEach { (label, icon, onClick) ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(label)) },
                                    leadingIcon = { Icon(icon, contentDescription = null) },
                                    onClick = {
                                        showMoreMenu = false
                                        onClick()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        ShiftStatusBanner(
            isShiftOpen = isShiftOpen,
            currentShift = currentShift,
            onOpenShiftClick = onOpenShiftClick,
            onXReportClick = onXReportClick,
            onCashMovementClick = onCashMovementClick,
            onCloseShiftClick = onCloseShiftClick
        )

        // Horizontal Category Filter Chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = CassyDimens.SpaceMd, vertical = CassyDimens.SpaceXs),
            horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceSm),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(categories, key = { it.first }) { (catId, catName) ->
                FilterChip(
                    selected = selectedCategoryId == catId,
                    onClick = { onCategorySelected(catId) },
                    label = { Text(catName) },
                    modifier = Modifier.defaultMinSize(minHeight = CassyDimens.TouchTargetMin)
                )
            }
        }

        // Product Catalog Grid
        PosCatalogGrid(
            gridWidth = totalWidth,
            displayedProducts = displayedProducts,
            searchQuery = searchQuery,
            onSearchQueryChange = onSearchQueryChange,
            isLoadingCatalog = isLoadingCatalog,
            catalogError = catalogError,
            onRetryCatalog = onRetryCatalog,
            onProductSelected = onProductSelected,
            rupiahFormat = rupiahFormat,
            modifier = Modifier.weight(1f)
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        // Sticky Mobile Bottom Cart Bar & Quick Action Buttons
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CassyDimens.SpaceMd, vertical = CassyDimens.SpaceSm),
                verticalArrangement = Arrangement.spacedBy(CassyDimens.SpaceSm)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = tableName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(
                                R.string.pos_mobile_cart_items_format,
                                totalItems,
                                totalAmountFormatted
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    CassyPrimaryButton(
                        text = stringResource(R.string.pos_mobile_view_cart),
                        icon = Icons.Rounded.ShoppingCart,
                        onClick = { showCartSheet = true }
                    )
                }

                if (cart.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceSm)
                    ) {
                        CassySecondaryButton(
                            text = stringResource(R.string.pos_mobile_quick_kitchen),
                            icon = Icons.Rounded.Restaurant,
                            enabled = !isSubmittingOrder,
                            onClick = onSendToKitchen,
                            modifier = Modifier.weight(1f)
                        )
                        CassySecondaryButton(
                            text = stringResource(R.string.pos_mobile_quick_cash),
                            icon = Icons.Rounded.Payments,
                            enabled = !isSubmittingOrder && quote != null,
                            onClick = onOpenCashModal,
                            modifier = Modifier.weight(1f)
                        )
                        CassyPrimaryButton(
                            text = stringResource(R.string.pos_mobile_quick_qris),
                            icon = Icons.Rounded.QrCode2,
                            enabled = !isSubmittingOrder && quote != null,
                            onClick = onOpenQrisModal,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }

    if (showCartSheet) {
        ModalBottomSheet(
            onDismissRequest = { showCartSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.88f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = CassyDimens.SpaceMd),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(
                        onClick = { showCartSheet = false },
                        modifier = Modifier.defaultMinSize(
                            minWidth = CassyDimens.TouchTargetMin,
                            minHeight = CassyDimens.TouchTargetMin
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.pos_mobile_close_cart)
                        )
                    }
                }
                PosOrderSummaryPanel(
                    panelWidth = totalWidth,
                    tableName = tableName,
                    orderType = orderType,
                    onTableNameChanged = onTableNameChanged,
                    onToggleOrderType = onToggleOrderType,
                    cart = cart,
                    isCartLocked = isCartLocked,
                    isShiftOpen = isShiftOpen,
                    isSubmittingOrder = isSubmittingOrder,
                    onIncreaseQuantity = onIncreaseQuantity,
                    onDecreaseQuantity = onDecreaseQuantity,
                    quote = quote,
                    isOffline = isOffline,
                    quoteError = quoteError,
                    rupiahFormat = rupiahFormat,
                    onOpenDrawerKick = onOpenDrawerKick,
                    onSendToKitchen = {
                        showCartSheet = false
                        onSendToKitchen()
                    },
                    onOpenCashModal = {
                        showCartSheet = false
                        onOpenCashModal()
                    },
                    onOpenQrisModal = {
                        showCartSheet = false
                        onOpenQrisModal()
                    },
                    onOpenEdcModal = {
                        showCartSheet = false
                        onOpenEdcModal()
                    },
                    failoverWarning = failoverWarning,
                    onReprintStations = onReprintStations,
                    tables = tables,
                    isLoadingTables = isLoadingTables,
                    selectedTableId = selectedTableId,
                    onOpenTableBoard = onOpenTableBoard,
                    onTableSelected = onTableSelected,
                    splitSection = splitSection,
                    splitBillEnabled = splitBillEnabled,
                    canReopenClosedTab = canReopenClosedTab,
                    hasLastSettledOrder = hasLastSettledOrder,
                    onReopenClosedTab = {
                        showCartSheet = false
                        onReopenClosedTab()
                    },
                    onOpenDigitalReceipt = {
                        showCartSheet = false
                        onOpenDigitalReceipt()
                    },
                    onOpenSplitBill = {
                        showCartSheet = false
                        onOpenSplitBill()
                    },
                    onOpenOpenBills = {
                        showCartSheet = false
                        onOpenOpenBills()
                    }
                )
            }
        }
    }
}
