package com.azuredevid.cassy.data.repository

import com.azuredevid.cassy.data.model.PosOrderDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class ApplyDiscountRequestDto(
    @SerialName("discount_amount") val discountAmount: Long? = null,
    @SerialName("discount_bps") val discountBps: Long? = null,
    val reason: String,
    @SerialName("owner_pin") val ownerPin: String? = null,
    @SerialName("approval_id") val approvalId: String? = null
)

@Serializable
internal data class VoidOrderRequestDto(
    val reason: String,
    val pin: String,
    @SerialName("owner_pin") val ownerPin: String? = null,
    @SerialName("approval_id") val approvalId: String? = null
)

@Serializable
internal data class OrderActionResponseDto(
    val order: PosOrderDto,
    val message: String = ""
)

@Serializable
internal data class RefundPaymentRequestDto(
    val reason: String,
    val pin: String,
    @SerialName("owner_pin") val ownerPin: String? = null,
    @SerialName("approval_id") val approvalId: String? = null,
    @SerialName("restore_stock") val restoreStock: Boolean,
    @SerialName("refund_method") val refundMethod: String = "cash",
    @SerialName("bank_name") val bankName: String = "",
    @SerialName("account_last4") val accountLast4: String = "",
    @SerialName("transfer_reference") val transferReference: String = ""
)

@Serializable
internal data class RefundPaymentResponseDto(
    val message: String = "",
    @SerialName("refund_status") val refundStatus: String = ""
)

@Serializable
internal data class ReprintOrderRequestDto(
    val kind: String = "ticket"
)

@Serializable
internal data class ReprintOrderResponseDto(
    val jobs: Int = 0
)

@Serializable
internal data class ReceiptLinkResponseDto(
    val url: String = "",
    @SerialName("short_code") val shortCode: String = "",
    @SerialName("expires_at") val expiresAt: String = ""
)

@Serializable
internal data class StockOverrideRequestDto(
    @SerialName("new_quantity") val newQuantity: Int,
    val reason: String,
    @SerialName("owner_pin") val ownerPin: String,
    @SerialName("approval_id") val approvalId: String? = null,
    @SerialName("enable_tracking") val enableTracking: Boolean = false
)

@Serializable
internal data class StockOverrideResponseDto(
    @SerialName("product_id") val productId: String,
    @SerialName("product_name") val productName: String = "",
    @SerialName("previous_quantity") val previousQuantity: Int = 0,
    @SerialName("new_quantity") val newQuantity: Int = 0,
    val delta: Int = 0
)

@Serializable
internal data class StartCustomerVerificationRequestDto(
    val phone: String,
    val name: String = ""
)

@Serializable
internal data class StartCustomerVerificationResponseDto(
    val id: String = "",
    @SerialName("verification_id") val verificationId: String = "",
    @SerialName("expires_at") val expiresAt: String = ""
)

@Serializable
internal data class ConfirmCustomerVerificationRequestDto(
    val code: String
)
