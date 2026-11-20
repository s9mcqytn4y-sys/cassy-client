package com.azuredevid.cassy.ui.pos.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallSplit
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PointOfSale
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.TableRestaurant
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.azuredevid.cassy.R
import com.azuredevid.cassy.data.model.OrderQuote
import com.azuredevid.cassy.domain.repository.PosTable
import com.azuredevid.cassy.ui.designsystem.CassyCard
import com.azuredevid.cassy.ui.designsystem.CassyDimens
import com.azuredevid.cassy.ui.designsystem.CassyEmptyState
import com.azuredevid.cassy.ui.designsystem.CassyErrorBanner
import com.azuredevid.cassy.ui.designsystem.CassyModalDialog
import com.azuredevid.cassy.ui.designsystem.CassyPrimaryButton
import com.azuredevid.cassy.ui.designsystem.CassySecondaryButton
import com.azuredevid.cassy.ui.designsystem.CassyStatusBadge
import com.azuredevid.cassy.ui.designsystem.CassyTextActionButton
import com.azuredevid.cassy.ui.designsystem.CassyTextField
import com.azuredevid.cassy.ui.designsystem.CassyToastTone
import com.azuredevid.cassy.ui.designsystem.CassyTonalButton
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
    quoteError: String? = null,
    rupiahFormat: NumberFormat,
    onOpenDrawerKick: () -> Unit,
    onSendToKitchen: () -> Unit,
    onOpenCashModal: () -> Unit,
    onOpenQrisModal: () -> Unit,
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
    splitBillEnabled: Boolean = true
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

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (cart.isEmpty()) {
                    CassyEmptyState(
                        icon = Icons.Rounded.ShoppingBag,
                        title = stringResource(R.string.cart_empty_title),
                        subtitle = stringResource(R.string.cart_empty_subtitle),
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(CassyDimens.SpaceSm)
                    ) {
                        items(cart, key = { "${it.product.id}-${it.isSentToKitchen}" }) { item ->
                            val decreaseDesc = stringResource(R.string.a11y_decrease_qty, item.product.name)
                            val increaseDesc = stringResource(R.string.a11y_increase_qty, item.product.name)
                            val lineTotal = item.product.basePriceIdr * item.quantity

                            CassyCard(contentPadding = CassyDimens.SpaceMd) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(end = CassyDimens.SpaceSm)
                                    ) {
                                        Text(
                                            text = item.product.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = stringResource(
                                                R.string.cash_chip_rp,
                                                rupiahFormat.format(lineTotal)
                                            ),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        if (item.isSentToKitchen) {
                                            Text(
                                                text = stringResource(R.string.cart_line_sent),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceXs)
                                    ) {
                                        OutlinedButton(
                                            onClick = { onDecreaseQuantity(item) },
                                            // A sent line can only be taken back as a void of a server tab line.
                                            enabled = !isCartLocked && (!item.isSentToKitchen || item.sentLines.isNotEmpty()),
                                            shape = RoundedCornerShape(CassyDimens.RadiusButton),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                            contentPadding = PaddingValues(0.dp),
                                            modifier = Modifier
                                                .size(CassyDimens.TouchTargetMin)
                                                .semantics { contentDescription = decreaseDesc }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Remove,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurface
                                            )
                                        }

                                        Text(
                                            text = "${item.quantity}",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.widthIn(min = 28.dp),
                                            textAlign = TextAlign.Center
                                        )

                                        OutlinedButton(
                                            onClick = { onIncreaseQuantity(item) },
                                            enabled = !isCartLocked,
                                            shape = RoundedCornerShape(CassyDimens.RadiusButton),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                            contentPadding = PaddingValues(0.dp),
                                            modifier = Modifier
                                                .size(CassyDimens.TouchTargetMin)
                                                .semantics { contentDescription = increaseDesc }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Add,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceSm)
                ) {
                    CassySecondaryButton(
                        text = stringResource(R.string.btn_kick_drawer),
                        onClick = onOpenDrawerKick,
                        icon = Icons.Rounded.PointOfSale,
                        modifier = Modifier.weight(1f)
                    )

                    if (orderType == "DINE-IN") {
                        CassyTonalButton(
                            text = stringResource(R.string.btn_send_to_kitchen),
                            onClick = onSendToKitchen,
                            enabled = cart.isNotEmpty() && !isCartLocked,
                            icon = Icons.Rounded.Restaurant,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceSm)
                ) {
                    CassyPrimaryButton(
                        text = stringResource(R.string.btn_pay_cash),
                        onClick = onOpenCashModal,
                        enabled = quote != null && cart.isNotEmpty() && !isCartLocked,
                        icon = Icons.Rounded.Payments,
                        height = CassyDimens.ButtonHeightTall,
                        modifier = Modifier.weight(1f)
                    )

                    CassyPrimaryButton(
                        text = stringResource(R.string.btn_pay_qris),
                        onClick = onOpenQrisModal,
                        enabled = isShiftOpen && quote != null && cart.isNotEmpty() && !isCartLocked && !isSubmittingOrder,
                        icon = Icons.Rounded.QrCode2,
                        height = CassyDimens.ButtonHeightTall,
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceSm)
                ) {
                    // A table bill is divided in splitSection when it closes; this split serves pay-first orders.
                    if (selectedTableId == null && splitBillEnabled) {
                        CassyTonalButton(
                            text = stringResource(R.string.split_btn_split_bill),
                            onClick = onOpenSplitBill,
                            enabled = isShiftOpen && quote != null && cart.isNotEmpty() && !isCartLocked && !isSubmittingOrder,
                            icon = Icons.AutoMirrored.Rounded.CallSplit,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    CassySecondaryButton(
                        text = stringResource(R.string.split_btn_open_bills),
                        onClick = onOpenOpenBills,
                        icon = Icons.AutoMirrored.Rounded.ReceiptLong,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    if (showTableDialog) {
        var draft by remember(tableName) { mutableStateOf(tableName) }
        CassyModalDialog(
            title = stringResource(R.string.order_table_dialog_title),
            icon = Icons.Rounded.TableRestaurant,
            maxWidth = CassyDimens.ModalWidthStandard,
            onDismissRequest = { showTableDialog = false },
            footer = {
                CassySecondaryButton(
                    text = stringResource(R.string.btn_cancel),
                    onClick = { showTableDialog = false },
                    modifier = Modifier.weight(1f)
                )
                CassyPrimaryButton(
                    text = stringResource(R.string.btn_apply),
                    onClick = {
                        onTableNameChanged(draft)
                        showTableDialog = false
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        ) {
            if (tables.isNotEmpty() || isLoadingTables) {
                PosTableBoard(
                    tables = tables,
                    isLoading = isLoadingTables,
                    selectedTableId = selectedTableId,
                    rupiahFormat = rupiahFormat,
                    onTableSelected = {
                        onTableSelected(it)
                        showTableDialog = false
                    }
                )
                Text(
                    text = stringResource(R.string.order_table_free_text),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            CassyTextField(
                value = draft,
                onValueChange = { draft = it },
                label = stringResource(R.string.order_table_input_label),
                placeholder = stringResource(R.string.order_table_input_hint)
            )
        }
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
