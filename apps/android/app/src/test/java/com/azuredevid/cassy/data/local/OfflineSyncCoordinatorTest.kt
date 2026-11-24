package com.azuredevid.cassy.data.local

import com.azuredevid.cassy.domain.model.CreatePosOrderInput
import com.azuredevid.cassy.domain.model.InitiatePosPaymentInput
import com.azuredevid.cassy.domain.model.InitiatePosPaymentResult
import com.azuredevid.cassy.domain.model.PosOrder
import com.azuredevid.cassy.domain.model.PosOrderDetail
import com.azuredevid.cassy.domain.model.PosPaymentAttempt
import com.azuredevid.cassy.domain.repository.DeviceOfflineOrderSyncItem
import com.azuredevid.cassy.domain.repository.DeviceOfflineOrderSyncOutcome
import com.azuredevid.cassy.domain.repository.PosOrderRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineSyncCoordinatorTest {

    private class InMemoryOfflineOrderDao : OfflineOrderDao {
        private val items = linkedMapOf<String, OfflinePendingOrderEntity>()
        private val countFlow = MutableStateFlow(0)

        override suspend fun getPendingOrders(): List<OfflinePendingOrderEntity> =
            items.values.filter { it.syncStatus == OfflinePendingOrderEntity.STATUS_PENDING }

        override fun observePendingCount(): Flow<Int> = countFlow.map {
            items.values.count { it.syncStatus == OfflinePendingOrderEntity.STATUS_PENDING }
        }

        override suspend fun upsert(order: OfflinePendingOrderEntity) {
            items[order.clientOrderId] = order
            countFlow.value = items.size
        }

        override suspend fun updateStatus(clientOrderId: String, status: String, error: String) {
            items[clientOrderId]?.let {
                items[clientOrderId] = it.copy(syncStatus = status, lastError = error)
            }
        }

        override suspend fun pruneSynced(olderThanMs: Long) {
            items.entries.removeIf { it.value.syncStatus == OfflinePendingOrderEntity.STATUS_SYNCED && it.value.createdAtMs < olderThanMs }
        }

        fun get(id: String): OfflinePendingOrderEntity? = items[id]
    }

    /** Accepts every order on the device endpoint; the online endpoints must never be used (S4-Q6). */
    private class FakePosOrderRepository : PosOrderRepository {
        val batches = mutableListOf<List<String>>()

        override suspend fun createOrder(input: CreatePosOrderInput): Result<PosOrder> =
            Result.failure(AssertionError("offline orders must not go through createOrder"))

        override suspend fun initiatePayment(input: InitiatePosPaymentInput): Result<InitiatePosPaymentResult> =
            Result.failure(AssertionError("offline orders must not go through initiatePayment"))

        override suspend fun getOrder(orderId: String): Result<PosOrderDetail> =
            Result.failure(UnsupportedOperationException())

        override suspend fun syncDeviceOfflineOrders(
            orders: List<DeviceOfflineOrderSyncItem>
        ): Result<List<DeviceOfflineOrderSyncOutcome>> {
            batches.add(orders.map { it.clientOrderId })
            delay(10)
            return Result.success(orders.map { DeviceOfflineOrderSyncOutcome(clientOrderId = it.clientOrderId, status = "accepted") })
        }
    }

    private suspend fun OfflineSyncCoordinator.enqueueWithMetadata(customer: String = "Budi", number: String? = "T1-101500") = enqueueCashOrder(
        storeSlug = "central-cafe",
        orderType = "takeaway",
        tableNumber = null,
        customerName = customer,
        items = listOf(OfflineOrderItemPayload(productId = "p1", quantity = 2)),
        cashReceivedIdr = 100_000L,
        cashierId = "00000000-0000-0000-0000-000000000001",
        shiftId = "00000000-0000-0000-0000-000000000002",
        catalogVersion = "v1",
        offlineQueueNumber = number
    )

    @Test
    fun `queued orders sync through the device endpoint and turn SYNCED`() = runTest {
        val dao = InMemoryOfflineOrderDao()
        val repo = FakePosOrderRepository()
        val coordinator = OfflineSyncCoordinator(dao, repo, StandardTestDispatcher(testScheduler))

        val entity = coordinator.enqueueWithMetadata()

        assertEquals(1, coordinator.syncPendingOrders())
        assertEquals(OfflinePendingOrderEntity.STATUS_SYNCED, dao.get(entity.clientOrderId)?.syncStatus)
        assertEquals(listOf(listOf(entity.clientOrderId)), repo.batches)
        assertEquals("a synced order is not sent again", 0, coordinator.syncPendingOrders())
    }

    @Test
    fun `concurrent syncs send each pending order once`() = runTest {
        val dao = InMemoryOfflineOrderDao()
        val repo = FakePosOrderRepository()
        val coordinator = OfflineSyncCoordinator(dao, repo, StandardTestDispatcher(testScheduler))
        coordinator.enqueueWithMetadata()

        val first = launch { coordinator.syncPendingOrders() }
        val second = launch { coordinator.syncPendingOrders() }
        first.join()
        second.join()

        assertEquals(1, repo.batches.size)
    }

    @Test
    fun `an order without shift metadata is kept as failed and never sent`() = runTest {
        val dao = InMemoryOfflineOrderDao()
        val repo = FakePosOrderRepository()
        val coordinator = OfflineSyncCoordinator(dao, repo, StandardTestDispatcher(testScheduler))
        val bare = coordinator.enqueueCashOrder("central-cafe", "takeaway", null, "Budi", listOf(OfflineOrderItemPayload("p1", 1)), 50_000L)

        assertEquals(0, coordinator.syncPendingOrders())
        assertEquals(OfflinePendingOrderEntity.STATUS_FAILED, dao.get(bare.clientOrderId)?.syncStatus)
        assertTrue(repo.batches.isEmpty())
    }

    /** The server rejects any offline number without the device prefix (OF6); none is invented on sync. */
    @Test
    fun `an order without an offline number is kept as failed and never sent`() = runTest {
        val dao = InMemoryOfflineOrderDao()
        val repo = FakePosOrderRepository()
        val coordinator = OfflineSyncCoordinator(dao, repo, StandardTestDispatcher(testScheduler))
        val unnumbered = coordinator.enqueueWithMetadata(number = null)

        assertEquals(0, coordinator.syncPendingOrders())
        assertEquals(OfflinePendingOrderEntity.STATUS_FAILED, dao.get(unnumbered.clientOrderId)?.syncStatus)
        assertTrue(repo.batches.isEmpty())
    }

    @Test
    fun `more than fifty orders go in batches the server accepts`() = runTest {
        val dao = InMemoryOfflineOrderDao()
        val repo = FakePosOrderRepository()
        val coordinator = OfflineSyncCoordinator(dao, repo, StandardTestDispatcher(testScheduler))
        repeat(51) { coordinator.enqueueWithMetadata(customer = "Tamu $it") }

        assertEquals(51, coordinator.syncPendingOrders())
        assertEquals(listOf(50, 1), repo.batches.map { it.size })
    }

    @Test
    fun `enqueue stops at the order cap and at the age cap`() = runTest {
        val item = listOf(OfflineOrderItemPayload("p1", 1))
        val byCount = OfflineSyncCoordinator(InMemoryOfflineOrderDao(), FakePosOrderRepository(), StandardTestDispatcher(testScheduler), maxOfflineOrders = 2)
        byCount.enqueueCashOrder("s", "take_away", null, "", item, 10_000L)
        byCount.enqueueCashOrder("s", "take_away", null, "", item, 10_000L)
        assertTrue(runCatching { byCount.enqueueCashOrder("s", "take_away", null, "", item, 10_000L) }.exceptionOrNull() is OfflineLimitReachedException)

        var now = 0L
        val byAge = OfflineSyncCoordinator(InMemoryOfflineOrderDao(), FakePosOrderRepository(), StandardTestDispatcher(testScheduler), nowMs = { now })
        byAge.enqueueCashOrder("s", "take_away", null, "", item, 10_000L)
        now = 12 * 3_600_000L + 1
        assertTrue(runCatching { byAge.enqueueCashOrder("s", "take_away", null, "", item, 10_000L) }.exceptionOrNull() is OfflineLimitReachedException)
    }

    @Test
    fun `device batch sync marks accepted orders synced and rejected orders failed`() = runTest {
        val dao = InMemoryOfflineOrderDao()
        val batchRepo = object : PosOrderRepository {
            override suspend fun createOrder(input: CreatePosOrderInput): Result<PosOrder> =
                Result.failure(AssertionError("createOrder should not be called for batch-synced entries"))

            override suspend fun initiatePayment(input: InitiatePosPaymentInput): Result<InitiatePosPaymentResult> =
                Result.failure(AssertionError("initiatePayment should not be called for batch-synced entries"))

            override suspend fun getOrder(orderId: String): Result<PosOrderDetail> =
                Result.failure(UnsupportedOperationException())

            override suspend fun syncDeviceOfflineOrders(
                orders: List<DeviceOfflineOrderSyncItem>
            ): Result<List<DeviceOfflineOrderSyncOutcome>> {
                return Result.success(
                    orders.mapIndexed { idx, item ->
                        if (idx == 0) {
                            DeviceOfflineOrderSyncOutcome(
                                clientOrderId = item.clientOrderId,
                                status = "accepted",
                                orderId = "srv-ord-1",
                                orderNumber = "ORD-001",
                                queueNumber = "A01"
                            )
                        } else {
                            DeviceOfflineOrderSyncOutcome(
                                clientOrderId = item.clientOrderId,
                                status = "rejected",
                                reason = "Shift mismatch"
                            )
                        }
                    }
                )
            }
        }
        val coordinator = OfflineSyncCoordinator(dao, batchRepo, StandardTestDispatcher(testScheduler))
        coordinator.updateOfflinePolicy(maxOrders = 50, maxHours = 6)

        val accepted = coordinator.enqueueCashOrder(
            storeSlug = "central-cafe",
            orderType = "take_away",
            tableNumber = null,
            customerName = "Ayu",
            items = listOf(OfflineOrderItemPayload("p1", 1)),
            cashReceivedIdr = 35_000L,
            cashierId = "00000000-0000-0000-0000-000000000001",
            shiftId = "00000000-0000-0000-0000-000000000002",
            catalogVersion = "2026-11-18T10:00:00Z",
            offlineQueueNumber = "T1-101501"
        )
        val rejected = coordinator.enqueueCashOrder(
            storeSlug = "central-cafe",
            orderType = "take_away",
            tableNumber = null,
            customerName = "Damar",
            items = listOf(OfflineOrderItemPayload("p1", 1)),
            cashReceivedIdr = 35_000L,
            cashierId = "00000000-0000-0000-0000-000000000001",
            shiftId = "00000000-0000-0000-0000-000000000002",
            catalogVersion = "2026-11-18T10:00:00Z",
            offlineQueueNumber = "T1-101502"
        )

        val syncedCount = coordinator.syncPendingOrders()
        assertEquals(1, syncedCount)
        assertEquals(OfflinePendingOrderEntity.STATUS_SYNCED, dao.get(accepted.clientOrderId)?.syncStatus)
        assertEquals(OfflinePendingOrderEntity.STATUS_FAILED, dao.get(rejected.clientOrderId)?.syncStatus)
        assertEquals("Shift mismatch", dao.get(rejected.clientOrderId)?.lastError)
    }
}
