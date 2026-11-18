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

interface PosOrderRepository {
    suspend fun createOrder(input: CreatePosOrderInput): Result<PosOrder>
    suspend fun initiatePayment(input: InitiatePosPaymentInput): Result<InitiatePosPaymentResult>
    suspend fun getOrder(orderId: String): Result<PosOrderDetail>
    suspend fun syncDeviceOfflineOrders(
        orders: List<DeviceOfflineOrderSyncItem>
    ): Result<List<DeviceOfflineOrderSyncOutcome>> =
        Result.failure(UnsupportedOperationException("device batch sync not configured"))
}
