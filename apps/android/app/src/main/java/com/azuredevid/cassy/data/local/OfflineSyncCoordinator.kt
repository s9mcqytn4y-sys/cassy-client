package com.azuredevid.cassy.data.local

import com.azuredevid.cassy.domain.model.CreatePosOrderInput
import com.azuredevid.cassy.domain.model.CreatePosOrderItemInput
import com.azuredevid.cassy.domain.model.InitiatePosPaymentInput
import com.azuredevid.cassy.domain.repository.PosOrderRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
 * Thrown by [OfflineSyncCoordinator.enqueueCashOrder] when the device already holds the maximum unsynced
 * orders, or its oldest unsynced order is older than the allowed window (ADR-005 OF7). The cashier must
 * reconnect and sync before taking more offline cash.
 */
class OfflineLimitReachedException : IllegalStateException("offline order limit reached")

/**
 * Queues offline cash orders in Room and replays them idempotently to the server on reconnect.
 * Server pricing remains authoritative once synced.
 */
class OfflineSyncCoordinator(
    private val offlineOrderDao: OfflineOrderDao,
    private val posOrderRepository: PosOrderRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    // Defaults mirror the server OF7 defaults (12 h, 200 orders).
    // shortcut: per-store overrides from store_settings are not read yet; plumb them in when POS offline checkout lands.
    private val maxOfflineOrders: Int = 200,
    private val maxOfflineHours: Long = 12,
    private val nowMs: () -> Long = System::currentTimeMillis
) {
    private val json = Json { ignoreUnknownKeys = true }

    // One sync at a time per process: a second caller waits, then finds nothing left to send.
    private val syncMutex = Mutex()

    suspend fun enqueueCashOrder(
        storeSlug: String,
        orderType: String,
        tableNumber: String?,
        customerName: String,
        items: List<OfflineOrderItemPayload>,
        cashReceivedIdr: Long
    ): OfflinePendingOrderEntity = withContext(ioDispatcher) {
        val pending = offlineOrderDao.getPendingOrders()
        val oldest = pending.firstOrNull()?.createdAtMs
        if (pending.size >= maxOfflineOrders ||
            (oldest != null && nowMs() - oldest > maxOfflineHours * 3_600_000L)
        ) {
            throw OfflineLimitReachedException()
        }
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
            createdAtMs = nowMs(),
            syncStatus = OfflinePendingOrderEntity.STATUS_PENDING
        )
        offlineOrderDao.upsert(entity)
        entity
    }

    suspend fun syncPendingOrders(): Int = syncMutex.withLock { withContext(ioDispatcher) {
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
    } }
}
