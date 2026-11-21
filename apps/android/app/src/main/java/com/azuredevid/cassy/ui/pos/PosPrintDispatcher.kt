package com.azuredevid.cassy.ui.pos

import com.azuredevid.cassy.core.hardware.DEFAULT_RECEIPT_FOOTER
import com.azuredevid.cassy.core.hardware.EscPosReceiptBuilder
import com.azuredevid.cassy.core.hardware.KdsTicketRouter
import com.azuredevid.cassy.core.hardware.PrinterPort9100Manager
import com.azuredevid.cassy.core.hardware.repeatCopies
import com.azuredevid.cassy.domain.model.PosOrder
import com.azuredevid.cassy.domain.repository.PrinterConfig
import com.azuredevid.cassy.domain.repository.TabSplit

/**
 * Pure ESC/POS receipt and station ticket dispatcher extracted from [PosViewModel]
 * to keep ViewModel state orchestration focused and under 300-400 lines.
 */
internal class PosPrintDispatcher(
    private val printerManager: PrinterPort9100Manager
) {

    suspend fun printShareReceipt(
        state: PosUiState,
        share: TabSplit,
        paymentLabel: String,
        cashTenderedIdr: Long?,
        changeIdr: Long?,
        config: PrinterConfig
    ) {
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
            lines = share.items.map { EscPosReceiptBuilder.ShareReceiptLine(it.name.ifBlank { "Item" }, it.quantity, it.amount) },
            paymentMethod = paymentLabel,
            cashTenderedIdr = cashTenderedIdr,
            changeIdr = changeIdr ?: cashTenderedIdr?.let { (it - share.totalAmount).coerceAtLeast(0L) }
        ).repeatCopies(config.receiptCopies)
        printerManager.printRawBytes(config.cashierPrinterIp, bytes, config.cashierPrinterPort)
    }

    suspend fun dispatchReceiptsAndKitchenTickets(
        state: PosUiState,
        order: PosOrder,
        cartSnapshot: List<CartItem>,
        tableLabel: String,
        paymentLabel: String,
        cashTenderedIdr: Long?,
        changeIdr: Long?,
        roundingIdr: Long,
        config: PrinterConfig,
        roundNumber: Int
    ): Boolean {
        val stations = buildStationConfigs(config)
        val hasAnyPrinter = config.hasCashierPrinter || stations.isNotEmpty()
        if (!hasAnyPrinter) return false

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
                storeName = state.storeName.ifBlank { "KASIR POS" },
                storeAddress = "",
                orderId = order.orderNumber.ifBlank { order.id },
                tableName = tableLabel,
                cashierName = state.cashierName.ifBlank { "Kasir" },
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
            items = cartSnapshot.filterNot { it.isSentToKitchen }.toStationItems(),
            stations = stations,
            cashierPrinterIp = config.cashierPrinterIp,
            cashierPrinterPort = config.cashierPrinterPort,
            cashierReceiptBytes = receiptBytes,
            deliveryMode = deliveryMode,
            roundNumber = roundNumber
        )
        return result.hasAnyFailover
    }

    fun buildStationConfigs(config: PrinterConfig): List<KdsTicketRouter.StationPrinterConfig> {
        val fallback = config.cashierPrinterIp.takeIf { it.isNotBlank() }
        val list = mutableListOf<KdsTicketRouter.StationPrinterConfig>()
        if (config.barPrinterIp.isNotBlank()) {
            list.add(KdsTicketRouter.StationPrinterConfig("bar", "BARISTA", config.barPrinterIp, fallback, config.barPrinterPort, config.barCopies))
        }
        if (config.kitchenPrinterIp.isNotBlank()) {
            list.add(KdsTicketRouter.StationPrinterConfig("kitchen", "DAPUR PANAS", config.kitchenPrinterIp, fallback, config.kitchenPrinterPort, config.kitchenCopies))
        }
        if (config.expediterPrinterIp.isNotBlank()) {
            list.add(KdsTicketRouter.StationPrinterConfig("runner", "RUNNER / EXPO", config.expediterPrinterIp, fallback, config.expediterPrinterPort, config.expediterCopies))
        }
        return list
    }

    fun resolveTableLabel(state: PosUiState): String {
        return if (state.orderType == "TAKEAWAY") {
            "Takeaway"
        } else {
            state.tableName.ifBlank { "Dine-In" }
        }
    }

    fun List<CartItem>.toStationItems(): List<KdsTicketRouter.StationItem> = map {
        KdsTicketRouter.StationItem(
            name = it.product.name,
            quantity = it.quantity,
            prepStation = it.product.prepStation,
            notes = it.notes
        )
    }
}
