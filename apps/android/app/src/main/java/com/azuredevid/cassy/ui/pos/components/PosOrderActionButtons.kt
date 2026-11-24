package com.azuredevid.cassy.ui.pos.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallSplit
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PointOfSale
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.TableRestaurant
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.azuredevid.cassy.R
import com.azuredevid.cassy.data.model.OrderQuote
import com.azuredevid.cassy.domain.repository.PosTable
import com.azuredevid.cassy.ui.designsystem.CassyDimens
import com.azuredevid.cassy.ui.designsystem.CassyModalDialog
import com.azuredevid.cassy.ui.designsystem.CassyPrimaryButton
import com.azuredevid.cassy.ui.designsystem.CassySecondaryButton
import com.azuredevid.cassy.ui.designsystem.CassyTextField
import com.azuredevid.cassy.ui.designsystem.CassyTonalButton
import com.azuredevid.cassy.ui.pos.CartItem
import java.text.NumberFormat

@Composable
internal fun PosOrderActionButtons(
    orderType: String,
    // Offline: cash only (ADR-005 OF1); kitchen send, tabs, split, QRIS and EDC wait for the server.
    isOffline: Boolean = false,
    cart: List<CartItem>,
    quote: OrderQuote?,
    isCartLocked: Boolean,
    isShiftOpen: Boolean,
    isSubmittingOrder: Boolean,
    selectedTableId: String?,
    splitBillEnabled: Boolean,
    canReopenClosedTab: Boolean,
    hasLastSettledOrder: Boolean,
    onOpenDrawerKick: () -> Unit,
    onSendToKitchen: () -> Unit,
    onOpenCashModal: () -> Unit,
    onOpenQrisModal: () -> Unit,
    onOpenEdcModal: () -> Unit,
    onOpenSplitBill: () -> Unit,
    onOpenOpenBills: () -> Unit,
    onReopenClosedTab: () -> Unit,
    onOpenDigitalReceipt: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CassyDimens.SpaceSm)
    ) {
        if (canReopenClosedTab) {
            CassyTonalButton(
                text = stringResource(R.string.pos_btn_reopen_tab),
                onClick = onReopenClosedTab,
                enabled = !isSubmittingOrder,
                icon = Icons.Rounded.LockOpen,
                modifier = Modifier.fillMaxWidth()
            )
        }

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
                    enabled = cart.isNotEmpty() && !isCartLocked && !isOffline,
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
                enabled = isShiftOpen && quote != null && cart.isNotEmpty() && !isCartLocked && !isSubmittingOrder && !isOffline,
                icon = Icons.Rounded.QrCode2,
                height = CassyDimens.ButtonHeightTall,
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary,
                modifier = Modifier.weight(1f)
            )
        }

        CassyTonalButton(
            text = stringResource(R.string.btn_pay_edc),
            onClick = onOpenEdcModal,
            enabled = quote != null && cart.isNotEmpty() && !isCartLocked && !isOffline,
            icon = Icons.Rounded.CreditCard,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceSm)
        ) {
            // A table bill is divided in splitSection when it closes; this split serves pay-first orders.
            if (selectedTableId == null && splitBillEnabled) {
                CassyTonalButton(
                    text = stringResource(R.string.split_btn_split_bill),
                    onClick = onOpenSplitBill,
                    enabled = isShiftOpen && quote != null && cart.isNotEmpty() && !isCartLocked && !isSubmittingOrder && !isOffline,
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

        if (hasLastSettledOrder && cart.isEmpty()) {
            CassySecondaryButton(
                text = stringResource(R.string.pos_btn_send_digital_receipt),
                onClick = onOpenDigitalReceipt,
                icon = Icons.AutoMirrored.Rounded.Send,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
internal fun PosTableSelectionDialog(
    tableName: String,
    tables: List<PosTable>,
    isLoadingTables: Boolean,
    selectedTableId: String?,
    rupiahFormat: NumberFormat,
    onTableNameChanged: (String) -> Unit,
    onTableSelected: (PosTable) -> Unit,
    onDismiss: () -> Unit
) {
    var draft by remember(tableName) { mutableStateOf(tableName) }
    CassyModalDialog(
        title = stringResource(R.string.order_table_dialog_title),
        icon = Icons.Rounded.TableRestaurant,
        maxWidth = CassyDimens.ModalWidthStandard,
        onDismissRequest = onDismiss,
        footer = {
            CassySecondaryButton(
                text = stringResource(R.string.btn_cancel),
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            )
            CassyPrimaryButton(
                text = stringResource(R.string.btn_apply),
                onClick = {
                    onTableNameChanged(draft)
                    onDismiss()
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
                    onDismiss()
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
