package com.azuredevid.cassy.domain.repository

import com.azuredevid.cassy.domain.model.CreatePosOrderInput
import com.azuredevid.cassy.domain.model.CreatePosOrderItemInput
import com.azuredevid.cassy.domain.model.InitiatePosPaymentInput
import com.azuredevid.cassy.domain.model.InitiatePosPaymentResult
import com.azuredevid.cassy.domain.model.PosOrder
import com.azuredevid.cassy.domain.model.PosOrderDetail

data class DeviceOfflineOrderSyncItem(
    val clientOrderId: String,
    val offlineQueueNumber: String,
    val offlineCreatedAtIso: String,
    val cashierId: String,
    val shiftId: String,
    val catalogVersion: String,
    val orderType: String,
    val customerName: String,
    val notes: String = "",
    val items: List<CreatePosOrderItemInput>,
    val cashTenderedAmount: Long,
    val printedLocally: Boolean = true
)

data class DeviceOfflineOrderSyncOutcome(
    val clientOrderId: String,
    val status: String,
    val reason: String = "",
    val orderId: String? = null,
    val orderNumber: String = "",
    val queueNumber: String = ""
)

data class ReceiptSendResult(
    val channel: String,
    val recipientMasked: String,
    val expiresAtIso: String
)

data class PosSalesChannelPrice(
    val variantId: String,
    val priceRupiah: Long
)

data class PosSalesChannel(
    val id: String,
    val name: String,
    val commissionBps: Long,
    val isActive: Boolean,
    val prices: List<PosSalesChannelPrice> = emptyList()
)

data class PosCustomerStamps(
    val customerId: String,
    val phone: String,
    val name: String,
    val verified: Boolean,
    val programActive: Boolean,
    val stamps: Long,
    val stampsRequired: Long,
    val rewardReady: Boolean,
    val rewardProductIds: List<String> = emptyList()
)

data class PosReceiptLink(
    val url: String,
    val shortCode: String = "",
    val expiresAtIso: String = ""
)

data class PosStockOverrideResult(
    val productId: String,
    val productName: String,
    val previousQuantity: Int,
    val newQuantity: Int,
    val delta: Int
)

data class PosCustomerVerificationStart(
    val verificationId: String,
    val expiresAtIso: String = ""
)

data class PosOrderVoidInput(
    val reason: String,
    val pin: String,
    val ownerPin: String? = null,
    val approvalId: String? = null
)

data class PosOrderDiscountInput(
    val discountAmount: Long? = null,
    val discountBps: Long? = null,
    val reason: String,
    val ownerPin: String? = null,
    val approvalId: String? = null
)

data class PosRefundPaymentInput(
    val reason: String,
    val pin: String,
    val ownerPin: String? = null,
    val approvalId: String? = null,
    val restoreStock: Boolean,
    val refundMethod: String = "cash",
    val bankName: String = "",
    val accountLast4: String = "",
    val transferReference: String = ""
)

interface PosOrderRepository {
    suspend fun createOrder(input: CreatePosOrderInput): Result<PosOrder>
    suspend fun initiatePayment(input: InitiatePosPaymentInput): Result<InitiatePosPaymentResult>
    suspend fun getOrder(orderId: String): Result<PosOrderDetail>
    suspend fun syncDeviceOfflineOrders(
        orders: List<DeviceOfflineOrderSyncItem>
    ): Result<List<DeviceOfflineOrderSyncOutcome>> =
        Result.failure(UnsupportedOperationException("device batch sync not configured"))

    /** Sends a digital receipt link via `email`, `whatsapp`, or `sms` (`POST /api/v1/pos/orders/{id}/receipt/send`). */
    suspend fun sendDigitalReceipt(orderId: String, channel: String, to: String): Result<ReceiptSendResult> =
        Result.failure(UnsupportedOperationException("sendDigitalReceipt not configured"))

    /** Lists active sales channels with channel-specific variant prices (`GET /api/v1/pos/sales-channels`). */
    suspend fun listSalesChannels(): Result<List<PosSalesChannel>> =
        Result.failure(UnsupportedOperationException("listSalesChannels not configured"))

    /** Looks up a customer by phone with live loyalty stamp count (`POST /api/v1/pos/customers/lookup`). */
    suspend fun lookupCustomerByPhone(phone: String): Result<PosCustomerStamps> =
        Result.failure(UnsupportedOperationException("lookupCustomerByPhone not configured"))

    /** Applies a fixed rupiah or percentage (bps) discount (`POST /api/v1/pos/orders/{id}/discount`). */
    suspend fun applyOrderDiscount(orderId: String, input: PosOrderDiscountInput): Result<PosOrder> =
        Result.failure(UnsupportedOperationException("applyOrderDiscount not configured"))

    /** Voids an unpaid order (`POST /api/v1/pos/orders/{id}/void`). */
    suspend fun voidOrder(orderId: String, input: PosOrderVoidInput): Result<PosOrder> =
        Result.failure(UnsupportedOperationException("voidOrder not configured"))

    /** Refunds a settled payment (`POST /api/v1/pos/orders/{id}/payments/{payment_id}/refund`). */
    suspend fun refundPayment(orderId: String, paymentId: String, input: PosRefundPaymentInput): Result<String> =
        Result.failure(UnsupportedOperationException("refundPayment not configured"))

    /** Enqueues station reprint jobs (`POST /api/v1/pos/orders/{id}/reprint`). */
    suspend fun reprintOrder(orderId: String, kind: String = "ticket"): Result<Int> =
        Result.failure(UnsupportedOperationException("reprintOrder not configured"))

    /** Issues a shareable public digital receipt link (`POST /api/v1/pos/orders/{id}/receipt-link`). */
    suspend fun issueReceiptLink(orderId: String): Result<PosReceiptLink> =
        Result.failure(UnsupportedOperationException("issueReceiptLink not configured"))

    /** Overrides tracked product stock quantity (`POST /api/v1/pos/products/{id}/stock-override`). */
    suspend fun overrideProductStock(
        productId: String,
        newQuantity: Int,
        reason: String,
        ownerPin: String,
        enableTracking: Boolean = false,
        approvalId: String? = null
    ): Result<PosStockOverrideResult> =
        Result.failure(UnsupportedOperationException("overrideProductStock not configured"))

    /** Starts SMS OTP verification for a customer phone (`POST /api/v1/pos/customers/verify`). */
    suspend fun startCustomerVerification(phone: String, name: String = ""): Result<PosCustomerVerificationStart> =
        Result.failure(UnsupportedOperationException("startCustomerVerification not configured"))

    /** Confirms SMS OTP verification for a customer phone (`POST /api/v1/pos/customers/verify/{id}/confirm`). */
    suspend fun confirmCustomerVerification(verificationId: String, code: String): Result<PosCustomerStamps> =
        Result.failure(UnsupportedOperationException("confirmCustomerVerification not configured"))
}
