package com.azuredevid.cassy.domain.model

/**
 * One share of a divided bill (backend `PaymentSplit`). Every amount is server-computed integer
 * rupiah; the client never derives a share total from items or prices.
 */
data class SplitShare(
    val id: String,
    val splitNumber: Int,
    val label: String,
    val mode: String,
    val totalAmount: Long,
    val paidAmount: Long,
    val status: String,
    val cashierRequestedAt: String? = null
) {
    /** What is still owed on this share, from the server's own amounts. */
    val remainingAmount: Long
        get() = (totalAmount - paidAmount).coerceAtLeast(0L)

    /** Only the server's status decides that a share is settled. */
    val isSettled: Boolean
        get() = status == STATUS_PAID

    /** Still collectable: not paid and not cancelled or refunded. */
    val isCollectable: Boolean
        get() = status == STATUS_UNPAID || status == STATUS_PENDING || status == STATUS_PARTIALLY_PAID

    /** True when the guest asked to settle this share at the cashier counter (P-06). */
    val isCashierRequested: Boolean
        get() = !cashierRequestedAt.isNullOrBlank() && isCollectable

    companion object {
        const val STATUS_UNPAID = "unpaid"
        const val STATUS_PENDING = "pending"
        const val STATUS_PARTIALLY_PAID = "partially_paid"
        const val STATUS_PAID = "paid"
    }
}

/**
 * A divided order with its shares, as the server reports it. [remainingAmount] is the server's
 * `total_amount - paid_amount`; the kitchen receives the order only when [isFullyPaid], which the
 * backend enforces by creating KDS tickets on the final settlement (partially paid orders get none).
 */
data class SplitBill(
    val order: PosOrder,
    val shares: List<SplitShare>
) {
    val remainingAmount: Long
        get() = (order.totalAmount - order.paidAmount).coerceAtLeast(0L)

    val isFullyPaid: Boolean
        get() = order.paymentStatus == PAYMENT_STATUS_PAID

    val collectableShares: List<SplitShare>
        get() = shares.filter { it.isCollectable }

    companion object {
        const val PAYMENT_STATUS_PAID = "paid"
        const val PAYMENT_STATUS_PARTIALLY_PAID = "partially_paid"
    }
}

/** How the cashier divides a pay-first order (`POST /api/v1/pos/orders/{id}/splits`). */
sealed interface SplitPlan {
    /** [count] equal shares; the server distributes rounding. */
    data class Equal(val count: Int) : SplitPlan

    /** Named shares, each with order lines and quantities; the server prices every share. */
    data class ByItem(val shares: List<ShareAllocation>) : SplitPlan
}

data class ShareAllocation(val label: String, val items: List<ItemAllocation>)

data class ItemAllocation(val orderItemId: String, val quantity: Int)

/** A divided order waiting for payment, from `GET /api/v1/pos/orders?payment_status=partially_paid`. */
data class OpenSplitBillSummary(
    val orderId: String,
    val orderNumber: String,
    val queueNumber: String,
    val tableNumber: String,
    val customerName: String,
    val totalAmount: Long,
    val paidAmount: Long
) {
    val remainingAmount: Long
        get() = (totalAmount - paidAmount).coerceAtLeast(0L)
}
