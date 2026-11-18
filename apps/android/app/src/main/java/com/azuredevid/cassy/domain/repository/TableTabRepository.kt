package com.azuredevid.cassy.domain.repository

import com.azuredevid.cassy.domain.model.CreatePosOrderItemInput
import com.azuredevid.cassy.domain.model.PosOrder

/** One table on the floor board, as `GET /api/v1/pos/tables` derives it (status, unpaid bill). */
data class PosTable(
    val id: String,
    val tableNumber: String,
    val label: String,
    val section: String,
    val capacity: Int,
    val status: String,
    val unpaidAmount: Long
) {
    val isFree: Boolean get() = status == "available" && unpaidAmount == 0L
}

/** One share of a closed tab. Amounts come from the server; each payment must name one share. */
data class TabSplit(
    val id: String,
    val number: Int,
    val totalAmount: Long,
    val paidAmount: Long = 0L,
    val status: String = "unpaid"
) {
    val isPaid: Boolean get() = status == "paid"
    val remainingAmount: Long get() = totalAmount - paidAmount
}

/** A closed tab ready to pay: the order plus one share (single payer) or 2 to 20 equal shares. */
data class ClosedTab(val order: PosOrder, val splits: List<TabSplit>)

/**
 * Takes [quantity] units off one sent line. [pin] is always the cashier's own; above the store's owner
 * threshold the server also needs [ownerPin] or an approved remote request ([approvalId]).
 */
data class TabItemVoidInput(
    val quantity: Int,
    val reason: String,
    val pin: String,
    val ownerPin: String? = null,
    val approvalId: String? = null
)

/**
 * Dine-in open bills (tabs) bound to a table. The server owns the bill: each kitchen send is a round,
 * and closing the tab freezes the total and creates the split the payment must name.
 */
interface TableTabRepository {
    suspend fun listTables(): Result<List<PosTable>>

    /**
     * Opens a tab for [tableId], or returns the tab already open on that table (with its items) when
     * another device or an earlier session opened it first.
     */
    suspend fun openTab(idempotencyKey: String, tableId: String, tableNumber: String): Result<PosOrder>

    suspend fun submitRound(orderId: String, idempotencyKey: String, items: List<CreatePosOrderItemInput>): Result<Unit>

    /** The open tab with every sent line (all rounds), each carrying the server line id a void needs. */
    suspend fun getTab(orderId: String): Result<PosOrder>

    /** Voids part or all of one line; returns the tab as it now stands. */
    suspend fun voidItem(orderId: String, itemId: String, input: TabItemVoidInput): Result<PosOrder>

    /** Freezes the bill. [splitCount] 1 makes one share; 2 to 20 asks the server for equal shares. */
    suspend fun closeTab(orderId: String, splitCount: Int = 1): Result<ClosedTab>
}
