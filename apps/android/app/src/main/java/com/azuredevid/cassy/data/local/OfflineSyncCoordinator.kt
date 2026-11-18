package com.azuredevid.cassy.data.local

import com.azuredevid.cassy.domain.model.CreatePosOrderInput
import com.azuredevid.cassy.domain.model.CreatePosOrderItemInput
import com.azuredevid.cassy.domain.model.InitiatePosPaymentInput
import com.azuredevid.cassy.domain.repository.DeviceOfflineOrderSyncItem
import com.azuredevid.cassy.domain.repository.PosOrderRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
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
    val items: List<OfflineOrderItemPayload>,
    val cashierId: String? = null,
    val shiftId: String? = null,
    val catalogVersion: String? = null,
    val offlineQueueNumber: String? = null,
    val offlineCreatedAtIso: String? = null
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
    maxOfflineOrders: Int = 200,
    maxOfflineHours: Long = 12,
    private val nowMs: () -> Long = System::currentTimeMillis
) {
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var currentMaxOfflineOrders: Int = maxOfflineOrders

    @Volatile
    private var currentMaxOfflineHours: Long = maxOfflineHours

    // One sync at a time per process: a second caller waits, then finds nothing left to send.
    private val syncMutex = Mutex()

    fun updateOfflinePolicy(maxOrders: Int, maxHours: Long) {
        if (maxOrders > 0) currentMaxOfflineOrders = maxOrders
        if (maxHours > 0) currentMaxOfflineHours = maxHours
    }

    suspend fun enqueueCashOrder(
        storeSlug: String,
        orderType: String,
        tableNumber: String?,
        customerName: String,
        items: List<OfflineOrderItemPayload>,
        cashReceivedIdr: Long,
        cashierId: String? = null,
        shiftId: String? = null,
        catalogVersion: String? = null,
        offlineQueueNumber: String? = null
    ): OfflinePendingOrderEntity = withContext(ioDispatcher) {
        val pending = offlineOrderDao.getPendingOrders()
        val oldest = pending.firstOrNull()?.createdAtMs
        if (pending.size >= currentMaxOfflineOrders ||
            (oldest != null && nowMs() - oldest > currentMaxOfflineHours * 3_600_000L)
        ) {
            throw OfflineLimitReachedException()
        }
        val createdMs = nowMs()
        val clientId = UUID.randomUUID().toString()
        val payload = OfflineOrderPayload(
            orderType = orderType,
            tableNumber = tableNumber,
            customerName = customerName,
            items = items,
            cashierId = cashierId,
            shiftId = shiftId,
            catalogVersion = catalogVersion,
            offlineQueueNumber = offlineQueueNumber,
            offlineCreatedAtIso = Instant.ofEpochMilli(createdMs).toString()
        )
        val entity = OfflinePendingOrderEntity(
            clientOrderId = clientId,
            storeSlug = storeSlug,
            idempotencyKey = "offline_$clientId",
            payloadJson = json.encodeToString(payload),
            cashReceivedIdr = cashReceivedIdr,
            createdAtMs = createdMs,
            syncStatus = OfflinePendingOrderEntity.STATUS_PENDING
        )
        offlineOrderDao.upsert(entity)
        entity
    }

    suspend fun syncPendingOrders(): Int = syncMutex.withLock { withContext(ioDispatcher) {
        val pending = offlineOrderDao.getPendingOrders()
        if (pending.isEmpty()) return@withContext 0

        var syncedCount = 0
        val decodedEntries = mutableListOf<Pair<OfflinePendingOrderEntity, OfflineOrderPayload>>()

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
            } else {
                decodedEntries.add(entry to payload)
            }
        }

        // First attempt batch sync against POST /api/v1/device/offline-orders/sync for entries with full shift metadata.
        val batchCandidates = decodedEntries.filter { (_, p) ->
            !p.cashierId.isNullOrBlank() && !p.shiftId.isNullOrBlank() && !p.catalogVersion.isNullOrBlank()
        }
        val handledIds = mutableSetOf<String>()

        if (batchCandidates.isNotEmpty()) {
            val batchItems = batchCandidates.mapIndexed { idx, (entry, p) ->
                DeviceOfflineOrderSyncItem(
                    clientOrderId = entry.clientOrderId,
                    offlineQueueNumber = p.offlineQueueNumber?.takeIf { it.isNotBlank() } ?: "OFF-${idx + 1}",
                    offlineCreatedAtIso = p.offlineCreatedAtIso
                        ?: Instant.ofEpochMilli(entry.createdAtMs).toString(),
                    cashierId = p.cashierId.orEmpty(),
                    shiftId = p.shiftId.orEmpty(),
                    catalogVersion = p.catalogVersion.orEmpty(),
                    orderType = p.orderType,
                    customerName = p.customerName,
                    items = p.items.map {
                        CreatePosOrderItemInput(
                            productId = it.productId,
                            quantity = it.quantity,
                            notes = it.notes
                        )
                    },
                    cashTenderedAmount = entry.cashReceivedIdr
                )
            }
            val batchResult = posOrderRepository.syncDeviceOfflineOrders(batchItems)
            batchResult.getOrNull()?.forEach { outcome ->
                handledIds.add(outcome.clientOrderId)
                when (outcome.status) {
                    "accepted", "resolved_accepted" -> {
                        offlineOrderDao.updateStatus(
                            outcome.clientOrderId,
                            OfflinePendingOrderEntity.STATUS_SYNCED
                        )
                        syncedCount++
                    }
                    "rejected", "resolved_rejected" -> {
                        offlineOrderDao.updateStatus(
                            outcome.clientOrderId,
                            OfflinePendingOrderEntity.STATUS_FAILED,
                            outcome.reason.ifBlank { "Rejected by server offline review" }
                        )
                    }
                }
            }
        }

        for ((entry, payload) in decodedEntries) {
            if (entry.clientOrderId in handledIds) continue

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
