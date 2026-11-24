package com.azuredevid.cassy.ui.pos.components

import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.azuredevid.cassy.R
import com.azuredevid.cassy.data.model.OrderQuote
import com.azuredevid.cassy.domain.repository.PosTable
import com.azuredevid.cassy.ui.designsystem.CassyDimens
import com.azuredevid.cassy.ui.designsystem.CassyErrorBanner
import com.azuredevid.cassy.ui.designsystem.CassyTextActionButton
import com.azuredevid.cassy.ui.designsystem.CassyToastTone
import com.azuredevid.cassy.ui.pos.CartItem
import com.azuredevid.cassy.ui.theme.statusErrBg
import com.azuredevid.cassy.ui.theme.statusErrFg
import com.azuredevid.cassy.ui.theme.statusWarnBg
import com.azuredevid.cassy.ui.theme.statusWarnFg
import java.text.NumberFormat

/**
 * Panel 3: Active order summary and settlement panel (35% width) on Cassy design primitives.
 */
@Composable
fun PosOrderSummaryPanel(
    panelWidth: Dp,
    tableName: String,
    orderType: String,
    onTableNameChanged: (String) -> Unit = {},
    onToggleOrderType: () -> Unit,
    cart: List<CartItem>,
    isCartLocked: Boolean,
    isShiftOpen: Boolean = true,
    isSubmittingOrder: Boolean = false,
    onIncreaseQuantity: (CartItem) -> Unit,
    onDecreaseQuantity: (CartItem) -> Unit,
    quote: OrderQuote?,
    isOffline: Boolean = false,
    quoteError: String? = null,
    rupiahFormat: NumberFormat,
    onOpenDrawerKick: () -> Unit,
    onSendToKitchen: () -> Unit,
    onOpenCashModal: () -> Unit,
    onOpenQrisModal: () -> Unit,
    onOpenEdcModal: () -> Unit,
    onOpenSplitBill: () -> Unit = {},
    onOpenOpenBills: () -> Unit = {},
    failoverWarning: String?,
    onReprintStations: () -> Unit,
    modifier: Modifier = Modifier,
    tables: List<PosTable> = emptyList(),
    isLoadingTables: Boolean = false,
    selectedTableId: String? = null,
    onOpenTableBoard: () -> Unit = {},
    onTableSelected: (PosTable) -> Unit = {},
    splitSection: @Composable () -> Unit = {},
    /** Owner flag `split_bill`; off hides new splits but keeps bills already split payable. */
    splitBillEnabled: Boolean = true,
    canReopenClosedTab: Boolean = false,
    hasLastSettledOrder: Boolean = false,
    onReopenClosedTab: () -> Unit = {},
    onOpenDigitalReceipt: () -> Unit = {}
) {
    var showTableDialog by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .width(panelWidth)
            .fillMaxHeight(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(CassyDimens.SpaceLg),
            verticalArrangement = Arrangement.spacedBy(CassyDimens.SpaceSm)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val headingLabel = if (orderType == "TAKEAWAY") {
                    stringResource(R.string.order_heading_takeaway)
                } else {
                    val displayTable = tableName.ifBlank { stringResource(R.string.order_table_unspecified) }
                    stringResource(R.string.order_heading, displayTable)
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = CassyDimens.TouchTargetMin)
                        .clickable(enabled = orderType == "DINE-IN" && !isCartLocked) {
                            showTableDialog = true
                            onOpenTableBoard()
                        }
                        .padding(end = CassyDimens.SpaceSm),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = headingLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (orderType == "DINE-IN" && !isCartLocked) {
                        Text(
                            text = stringResource(R.string.order_table_tap_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (isCartLocked) {
                        Text(
                            text = stringResource(R.string.cart_locked_notice),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                val typeText = if (orderType == "DINE-IN") {
                    stringResource(R.string.order_type_dine_in)
                } else {
                    stringResource(R.string.order_type_takeaway)
                }
                Surface(
                    shape = RoundedCornerShape(CassyDimens.RadiusButton),
                    color = if (orderType == "DINE-IN") {
                        statusWarnBg()
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    },
                    border = BorderStroke(
                        1.dp,
                        if (orderType == "DINE-IN") {
                            statusWarnFg().copy(alpha = 0.25f)
                        } else {
                            MaterialTheme.colorScheme.outline
                        }
                    ),
                    modifier = Modifier
                        .defaultMinSize(minHeight = CassyDimens.TouchTargetMin)
                        .clickable(enabled = !isCartLocked) { onToggleOrderType() }
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(
                            horizontal = CassyDimens.SpaceMd,
                            vertical = CassyDimens.SpaceXs
                        )
                    ) {
                        Text(
                            text = typeText,
                            color = if (orderType == "DINE-IN") {
                                statusWarnFg()
                            } else {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (!failoverWarning.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(CassyDimens.RadiusCard),
                    color = statusErrBg(),
                    border = BorderStroke(1.dp, statusErrFg().copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = CassyDimens.SpaceMd, vertical = CassyDimens.SpaceXs),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = failoverWarning,
                            style = MaterialTheme.typography.labelSmall,
                            color = statusErrFg(),
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        CassyTextActionButton(
                            text = stringResource(R.string.btn_reprint_stations),
                            onClick = onReprintStations,
                            contentColor = statusErrFg()
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            PosCartItemsList(
                cart = cart,
                isCartLocked = isCartLocked,
                rupiahFormat = rupiahFormat,
                onIncreaseQuantity = onIncreaseQuantity,
                onDecreaseQuantity = onDecreaseQuantity,
                modifier = Modifier.weight(1f)
            )

            Column(verticalArrangement = Arrangement.spacedBy(CassyDimens.SpaceSm)) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                when {
                    quote == null && !quoteError.isNullOrBlank() -> {
                        CassyErrorBanner(message = quoteError, tone = CassyToastTone.ERROR)
                    }
                    quote == null && cart.isNotEmpty() -> {
                        Text(
                            text = stringResource(R.string.total_from_server),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    quote != null -> {
                        OrderSummaryLine(
                            label = stringResource(R.string.subtotal_label),
                            amount = stringResource(R.string.cash_chip_rp, rupiahFormat.format(quote.subtotalAmount))
                        )
                        if (quote.serviceChargeAmount > 0) {
                            OrderSummaryLine(
                                label = stringResource(R.string.service_label),
                                amount = stringResource(
                                    R.string.cash_chip_rp,
                                    rupiahFormat.format(quote.serviceChargeAmount)
                                )
                            )
                        }
                        OrderSummaryLine(
                            label = stringResource(R.string.tax_pb1_label),
                            amount = stringResource(R.string.cash_chip_rp, rupiahFormat.format(quote.taxAmount))
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.grand_total_label),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.cash_chip_rp, rupiahFormat.format(quote.totalAmount)),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                splitSection()

                if (isOffline) {
                    CassyErrorBanner(
                        message = stringResource(R.string.pos_offline_banner),
                        tone = CassyToastTone.WARNING,
                        modifier = Modifier.testTag("banner_pos_offline")
                    )
                }

                PosOrderActionButtons(
                    orderType = orderType,
                    isOffline = isOffline,
                    cart = cart,
                    quote = quote,
                    isCartLocked = isCartLocked,
                    isShiftOpen = isShiftOpen,
                    isSubmittingOrder = isSubmittingOrder,
                    selectedTableId = selectedTableId,
                    splitBillEnabled = splitBillEnabled,
                    canReopenClosedTab = canReopenClosedTab,
                    hasLastSettledOrder = hasLastSettledOrder,
                    onOpenDrawerKick = onOpenDrawerKick,
                    onSendToKitchen = onSendToKitchen,
                    onOpenCashModal = onOpenCashModal,
                    onOpenQrisModal = onOpenQrisModal,
                    onOpenEdcModal = onOpenEdcModal,
                    onOpenSplitBill = onOpenSplitBill,
                    onOpenOpenBills = onOpenOpenBills,
                    onReopenClosedTab = onReopenClosedTab,
                    onOpenDigitalReceipt = onOpenDigitalReceipt
                )
            }
        }
    }

    if (showTableDialog) {
        PosTableSelectionDialog(
            tableName = tableName,
            tables = tables,
            isLoadingTables = isLoadingTables,
            selectedTableId = selectedTableId,
            rupiahFormat = rupiahFormat,
            onTableNameChanged = onTableNameChanged,
            onTableSelected = onTableSelected,
            onDismiss = { showTableDialog = false }
        )
    }
}

@Composable
private fun OrderSummaryLine(
    label: String,
    amount: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = amount,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
