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
}
