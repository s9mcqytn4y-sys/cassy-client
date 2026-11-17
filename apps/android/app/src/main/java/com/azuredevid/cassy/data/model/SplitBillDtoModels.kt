package com.azuredevid.cassy.data.model

import com.azuredevid.cassy.domain.model.ItemAllocation
import com.azuredevid.cassy.domain.model.OpenSplitBillSummary
import com.azuredevid.cassy.domain.model.ShareAllocation
import com.azuredevid.cassy.domain.model.SplitBill
import com.azuredevid.cassy.domain.model.SplitPlan
import com.azuredevid.cassy.domain.model.SplitShare
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Backend `PaymentSplit`. */
@Serializable
data class PaymentSplitDto(
    @SerialName("id") val id: String,
    @SerialName("split_number") val splitNumber: Int = 0,
    @SerialName("label") val label: String = "",
    @SerialName("split_mode") val splitMode: String = "",
    @SerialName("total_amount") val totalAmount: Long = 0L,
    @SerialName("paid_amount") val paidAmount: Long = 0L,
    @SerialName("status") val status: String = ""
) {
    fun toDomain() = SplitShare(
        id = id,
        splitNumber = splitNumber,
        label = label,
        mode = splitMode,
        totalAmount = totalAmount,
        paidAmount = paidAmount,
        status = status
    )
}

/** `GET /api/v1/pos/orders/{id}` read only for the order and its shares. */
@Serializable
data class OrderWithSplitsDto(
    @SerialName("order") val order: PosOrderDto,
    @SerialName("splits") val splits: List<PaymentSplitDto> = emptyList()
) {
    fun toDomain() = SplitBill(order = order.toDomain(), shares = splits.map { it.toDomain() }.sortedBy { it.splitNumber })
}

@Serializable
data class DivideOrderRequestDto(
    @SerialName("strategy") val strategy: String,
    @SerialName("split_count") val splitCount: Int? = null,
    @SerialName("splits") val splits: List<DivideShareDto>? = null
) {
    companion object {
        fun from(plan: SplitPlan) = when (plan) {
            is SplitPlan.Equal -> DivideOrderRequestDto(strategy = "equal", splitCount = plan.count)
            is SplitPlan.ByItem -> DivideOrderRequestDto(strategy = "by_item", splits = plan.shares.map(DivideShareDto::from))
        }
    }
}

@Serializable
data class DivideShareDto(
    @SerialName("label") val label: String,
    @SerialName("items") val items: List<DivideItemDto>
) {
    companion object {
        fun from(share: ShareAllocation) = DivideShareDto(share.label.trim(), share.items.map(DivideItemDto::from))
    }
}

@Serializable
data class DivideItemDto(
    @SerialName("order_item_id") val orderItemId: String,
    @SerialName("quantity") val quantity: Int
) {
    companion object {
        fun from(item: ItemAllocation) = DivideItemDto(item.orderItemId, item.quantity)
    }
}

/** `POST /api/v1/pos/orders/{id}/splits` response: the order and its new shares. */
@Serializable
data class DivideOrderResponseDto(
    @SerialName("order") val order: PosOrderDto,
    @SerialName("splits") val splits: List<PaymentSplitDto> = emptyList()
) {
    fun toDomain() = SplitBill(order = order.toDomain(), shares = splits.map { it.toDomain() }.sortedBy { it.splitNumber })
}

@Serializable
data class OrderSummaryDto(
    @SerialName("id") val id: String,
    @SerialName("order_number") val orderNumber: String = "",
    @SerialName("queue_number") val queueNumber: String = "",
    @SerialName("table_number") val tableNumber: String = "",
    @SerialName("customer_name") val customerName: String = "",
    @SerialName("total_amount") val totalAmount: Long = 0L,
    @SerialName("paid_amount") val paidAmount: Long = 0L
) {
    fun toDomain() = OpenSplitBillSummary(
        orderId = id,
        orderNumber = orderNumber,
        queueNumber = queueNumber,
        tableNumber = tableNumber,
        customerName = customerName,
        totalAmount = totalAmount,
        paidAmount = paidAmount
    )
}

/** `GET /api/v1/pos/orders` page (`OrderList`). */
@Serializable
data class OrderListResponseDto(
    @SerialName("items") val items: List<OrderSummaryDto> = emptyList()
)
