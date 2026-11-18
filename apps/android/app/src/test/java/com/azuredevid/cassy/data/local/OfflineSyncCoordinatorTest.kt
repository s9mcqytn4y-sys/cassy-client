package com.azuredevid.cassy.data.local

import com.azuredevid.cassy.domain.model.CreatePosOrderInput
import com.azuredevid.cassy.domain.model.InitiatePosPaymentInput
import com.azuredevid.cassy.domain.model.InitiatePosPaymentResult
import com.azuredevid.cassy.domain.model.PosOrder
import com.azuredevid.cassy.domain.model.PosOrderDetail
import com.azuredevid.cassy.domain.model.PosPaymentAttempt
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

    private class FakePosOrderRepository : PosOrderRepository {
        var createdKeys = mutableListOf<String>()
        var paymentKeys = mutableListOf<String>()

        override suspend fun createOrder(input: CreatePosOrderInput): Result<PosOrder> {
            createdKeys.add(input.idempotencyKey)
            delay(10)
            return Result.success(PosOrder(id = "ord-1", orderNumber = "A-001", totalAmount = 50_000L))
        }

        override suspend fun initiatePayment(input: InitiatePosPaymentInput): Result<InitiatePosPaymentResult> {
            paymentKeys.add(input.idempotencyKey)
            return Result.success(
                InitiatePosPaymentResult(
                    payment = PosPaymentAttempt(
                        id = "pay-1",
                        providerOrderId = "prov-1",
                        method = input.method,
                        status = "paid",
                        amount = 50_000L,
                        cashTenderedAmount = input.cashTenderedAmount
                    )
                )
            )
        }

        override suspend fun getOrder(orderId: String): Result<PosOrderDetail> {
            return Result.success(PosOrderDetail(order = PosOrder(id = orderId, orderNumber = "A-001")))
        }
    }

    @Test
    fun `enqueueCashOrder and syncPendingOrders transitions to SYNCED idempotently`() = runTest {
        val dao = InMemoryOfflineOrderDao()
        val repo = FakePosOrderRepository()
        val coordinator = OfflineSyncCoordinator(dao, repo)

        val entity = coordinator.enqueueCashOrder(
            storeSlug = "central-cafe",
            orderType = "take_away",
            tableNumber = null,
            customerName = "Budi",
            items = listOf(OfflineOrderItemPayload(productId = "p1", quantity = 2)),
            cashReceivedIdr = 100_000L
        )

        val synced = coordinator.syncPendingOrders()
        assertEquals(1, synced)
        assertEquals(OfflinePendingOrderEntity.STATUS_SYNCED, dao.get(entity.clientOrderId)?.syncStatus)
        assertEquals(listOf(entity.idempotencyKey), repo.createdKeys)
        assertEquals(listOf("${entity.idempotencyKey}_cash"), repo.paymentKeys)
    }

    @Test
    fun `concurrent syncs send each pending order once`() = runTest {
        val dao = InMemoryOfflineOrderDao()
        val repo = FakePosOrderRepository()
        val coordinator = OfflineSyncCoordinator(dao, repo, StandardTestDispatcher(testScheduler))
        coordinator.enqueueCashOrder("central-cafe", "take_away", null, "Budi", listOf(OfflineOrderItemPayload("p1", 1)), 50_000L)

        val first = launch { coordinator.syncPendingOrders() }
        val second = launch { coordinator.syncPendingOrders() }
        first.join()
        second.join()

        assertEquals(1, repo.createdKeys.size)
        assertEquals(1, repo.paymentKeys.size)
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
}
