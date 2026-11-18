package com.azuredevid.cassy.data.local

import com.azuredevid.cassy.domain.model.CreatePosOrderInput
import com.azuredevid.cassy.domain.model.CreatePosOrderItemInput
import com.azuredevid.cassy.domain.model.InitiatePosPaymentInput
import com.azuredevid.cassy.domain.repository.PosOrderRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
data class OfflineOrderItemPayload(
    val productId: String,
    val quantity: Int,
    val notes: String = ""
)

@Serializable
data class OfflineOrderPayload(
    val orderType: String,
    val tableNumber: String? = null,
    val customerName: String = "",
    val items: List<OfflineOrderItemPayload>
)

/**
 * Queues offline cash orders in Room and replays them idempotently to the server on reconnect.
 * Server pricing remains authoritative once synced.
 */
class OfflineSyncCoordinator(
    private val offlineOrderDao: OfflineOrderDao,
    private val posOrderRepository: PosOrderRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun enqueueCashOrder(
        storeSlug: String,
        orderType: String,
        tableNumber: String?,
        customerName: String,
        items: List<OfflineOrderItemPayload>,
        cashReceivedIdr: Long
    ): OfflinePendingOrderEntity = withContext(ioDispatcher) {
        val clientId = UUID.randomUUID().toString()
        val payload = OfflineOrderPayload(
            orderType = orderType,
            tableNumber = tableNumber,
            customerName = customerName,
            items = items
        )
        val entity = OfflinePendingOrderEntity(
            clientOrderId = clientId,
            storeSlug = storeSlug,
            idempotencyKey = "offline_$clientId",
            payloadJson = json.encodeToString(payload),
            cashReceivedIdr = cashReceivedIdr,
            createdAtMs = System.currentTimeMillis(),
            syncStatus = OfflinePendingOrderEntity.STATUS_PENDING
        )
        offlineOrderDao.upsert(entity)
        entity
    }

    suspend fun syncPendingOrders(): Int = withContext(ioDispatcher) {
        val pending = offlineOrderDao.getPendingOrders()
        var syncedCount = 0
        for (entry in pending) {
            val payload = runCatching {
                json.decodeFromString<OfflineOrderPayload>(entry.payloadJson)
            }.getOrNull()
            if (payload == null) {
                offlineOrderDao.updateStatus(
                    entry.clientOrderId,
                    OfflinePendingOrderEntity.STATUS_FAILED,
                    "Corrupt offline payload"
                )
                continue
            }
            val createResult = posOrderRepository.createOrder(
                CreatePosOrderInput(
                    idempotencyKey = entry.idempotencyKey,
                    orderType = payload.orderType,
                    tableNumber = payload.tableNumber,
                    customerName = payload.customerName,
                    items = payload.items.map {
                        CreatePosOrderItemInput(
                            productId = it.productId,
                            quantity = it.quantity,
                            notes = it.notes
                        )
                    }
                )
            )
            val createdOrder = createResult.getOrNull()
            if (createdOrder == null) {
                offlineOrderDao.updateStatus(
                    entry.clientOrderId,
                    OfflinePendingOrderEntity.STATUS_PENDING,
                    createResult.exceptionOrNull()?.message.orEmpty()
                )
                continue
            }
            val tenderResult = posOrderRepository.initiatePayment(
                InitiatePosPaymentInput(
                    orderId = createdOrder.id,
                    idempotencyKey = "${entry.idempotencyKey}_cash",
                    method = "cash",
                    cashTenderedAmount = entry.cashReceivedIdr
                )
            )
            if (tenderResult.isSuccess) {
                offlineOrderDao.updateStatus(entry.clientOrderId, OfflinePendingOrderEntity.STATUS_SYNCED)
                syncedCount++
            } else {
                offlineOrderDao.updateStatus(
                    entry.clientOrderId,
                    OfflinePendingOrderEntity.STATUS_PENDING,
                    tenderResult.exceptionOrNull()?.message.orEmpty()
                )
            }
        }
        syncedCount
    }
}
