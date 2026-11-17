package com.azuredevid.cassy.ui.pos.split

import com.azuredevid.cassy.R
import com.azuredevid.cassy.domain.error.DomainError
import com.azuredevid.cassy.domain.error.DomainException
import com.azuredevid.cassy.domain.model.CreatePosOrderInput
import com.azuredevid.cassy.domain.model.InitiatePosPaymentInput
import com.azuredevid.cassy.domain.model.InitiatePosPaymentResult
import com.azuredevid.cassy.domain.model.ItemAllocation
import com.azuredevid.cassy.domain.model.OpenSplitBillSummary
import com.azuredevid.cassy.domain.model.PosOrder
import com.azuredevid.cassy.domain.model.PosOrderDetail
import com.azuredevid.cassy.domain.model.PosPaymentAttempt
import com.azuredevid.cassy.domain.model.ShareAllocation
import com.azuredevid.cassy.domain.model.SplitBill
import com.azuredevid.cassy.domain.model.SplitPlan
import com.azuredevid.cassy.domain.model.SplitShare
import com.azuredevid.cassy.domain.repository.PosOrderRepository
import com.azuredevid.cassy.domain.repository.SplitBillRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SplitSettlementViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun order(paymentStatus: String, paid: Long, total: Long = 90_000L) =
        PosOrder(id = "ord-1", orderNumber = "ORD-9", paymentStatus = paymentStatus, totalAmount = total, paidAmount = paid)

    private fun share(n: Int, total: Long, paid: Long = 0L, status: String = SplitShare.STATUS_UNPAID) =
        SplitShare(id = "sp-$n", splitNumber = n, label = "", mode = "equal", totalAmount = total, paidAmount = paid, status = status)

    private val undivided = SplitBill(order("pending", 0L), listOf(share(1, 90_000L)))
    private val divided = SplitBill(order("pending", 0L), listOf(share(1, 30_000L), share(2, 30_000L), share(3, 30_000L)))

    private class FakeSplitBills : SplitBillRepository {
        val bills = ArrayDeque<Result<SplitBill>>()
        var lastBill: SplitBill? = null
        var divideResult: Result<SplitBill>? = null
        val dividePlans = mutableListOf<SplitPlan>()
        var getBillCalls = 0

        override suspend fun divide(orderId: String, plan: SplitPlan): Result<SplitBill> {
            dividePlans += plan
            return divideResult!!
        }

        override suspend fun getBill(orderId: String): Result<SplitBill> {
            getBillCalls++
            val next = bills.removeFirstOrNull() ?: Result.success(lastBill!!)
            next.onSuccess { lastBill = it }
            return next
        }

        override suspend fun listOpenBills(query: String): Result<List<OpenSplitBillSummary>> =
            Result.success(listOf(OpenSplitBillSummary("ord-1", "ORD-9", "", "5", "", 90_000L, 30_000L)))
    }

    private class FakePosOrders : PosOrderRepository {
        val inputs = mutableListOf<InitiatePosPaymentInput>()
        val results = ArrayDeque<Result<InitiatePosPaymentResult>>()

        override suspend fun createOrder(input: CreatePosOrderInput): Result<PosOrder> = Result.failure(UnsupportedOperationException())
        override suspend fun getOrder(orderId: String): Result<PosOrderDetail> = Result.failure(UnsupportedOperationException())
        override suspend fun initiatePayment(input: InitiatePosPaymentInput): Result<InitiatePosPaymentResult> {
            inputs += input
            return results.removeFirst()
        }
    }

    private fun cashOk(amount: Long, change: Long, openDrawer: Boolean = true) = Result.success(
        InitiatePosPaymentResult(
            openDrawer = openDrawer,
            payment = PosPaymentAttempt(
                id = "pay", providerOrderId = "p", method = "cash", status = "settled",
                amount = amount, changeGivenAmount = change
            )
        )
    )

    private class Harness(val vm: SplitSettlementViewModel, val effects: MutableList<SplitSettlementEffect>)

    private fun TestScope.harness(splits: FakeSplitBills, orders: FakePosOrders): Harness {
        val vm = SplitSettlementViewModel(splits, orders)
        val effects = mutableListOf<SplitSettlementEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.effects.collect { effects += it } }
        return Harness(vm, effects)
    }

    @Test
    fun undividedOrder_opensPlanChoice_thenEqualDivideMovesToSettle() = runTest {
        val splits = FakeSplitBills().apply { bills += Result.success(undivided); divideResult = Result.success(divided) }
        val h = harness(splits, FakePosOrders())

        h.vm.open("ord-1"); advanceUntilIdle()
        assertEquals(SplitStage.CHOOSE_PLAN, h.vm.uiState.value.stage)

        h.vm.divide(SplitPlan.Equal(3)); advanceUntilIdle()
        assertEquals(SplitPlan.Equal(3), splits.dividePlans.single())
        assertEquals(SplitStage.SETTLE, h.vm.uiState.value.stage)
        assertEquals(listOf(30_000L, 30_000L, 30_000L), h.vm.uiState.value.bill!!.shares.map { it.totalAmount })
    }

    @Test
    fun invalidPlans_areRejectedWithoutCallingServer() = runTest {
        val splits = FakeSplitBills().apply { bills += Result.success(undivided) }
        val h = harness(splits, FakePosOrders())
        h.vm.open("ord-1"); advanceUntilIdle()

        h.vm.divide(SplitPlan.Equal(1)); advanceUntilIdle()
        assertEquals(SplitMessage.Res(R.string.split_error_plan), h.vm.uiState.value.message)

        h.vm.divide(
            SplitPlan.ByItem(
                listOf(
                    ShareAllocation("Orang 1", listOf(ItemAllocation("it-1", 2))),
                    ShareAllocation("Orang 2", emptyList())
                )
            )
        ); advanceUntilIdle()
        assertTrue(splits.dividePlans.isEmpty())
    }

    @Test
    fun partialSettlement_keepsKitchenBlockedAndSendsSplitId() = runTest {
        val afterFirst = SplitBill(
            order("partially_paid", 30_000L),
            listOf(share(1, 30_000L, 30_000L, SplitShare.STATUS_PAID), share(2, 30_000L), share(3, 30_000L))
        )
        val splits = FakeSplitBills().apply { bills += Result.success(divided); bills += Result.success(afterFirst) }
        val orders = FakePosOrders().apply { results += cashOk(30_000L, 20_000L) }
        val h = harness(splits, orders)
        h.vm.open("ord-1"); advanceUntilIdle()
        assertEquals(SplitStage.SETTLE, h.vm.uiState.value.stage)

        h.vm.startCashForShare("sp-1")
        assertEquals("sp-1", h.vm.uiState.value.cashShareId)
        h.vm.payShareCash("sp-1", tendered = 50_000L, drawerId = "drw-1"); advanceUntilIdle()

        val sent = orders.inputs.single()
        assertEquals("sp-1", sent.splitId)
        assertEquals("cash", sent.method)
        assertEquals(50_000L, sent.cashTenderedAmount)
        val state = h.vm.uiState.value
        assertNull(state.cashShareId)
        assertEquals(20_000L, state.lastChangeGiven)
        assertEquals(60_000L, state.bill!!.remainingAmount)
        assertEquals(listOf<SplitSettlementEffect>(SplitSettlementEffect.OpenDrawer), h.effects)
    }

    @Test
    fun cashRemainder_collectsOnlyWhatTheShareStillOwes() = runTest {
        val partlyPaid = SplitBill(
            order("partially_paid", 10_000L, total = 60_000L),
            listOf(share(1, 30_000L, 10_000L, SplitShare.STATUS_PARTIALLY_PAID), share(2, 30_000L))
        )
        val splits = FakeSplitBills().apply { bills += Result.success(partlyPaid) }
        val h = harness(splits, FakePosOrders())
        h.vm.open("ord-1"); advanceUntilIdle()

        val first = h.vm.uiState.value.bill!!.shares.first()
        assertEquals(SplitStage.SETTLE, h.vm.uiState.value.stage)
        assertTrue(first.isCollectable)
        assertEquals(20_000L, first.remainingAmount)
        assertEquals(50_000L, h.vm.uiState.value.bill!!.remainingAmount)
    }

    @Test
    fun billSettled_firesOnlyWhenServerReportsPaid() = runTest {
        val lastOpen = SplitBill(
            order("partially_paid", 60_000L),
            listOf(share(1, 30_000L, 30_000L, "paid"), share(2, 30_000L, 30_000L, "paid"), share(3, 30_000L))
        )
        // Server still says partially_paid even though the client could add the shares up: no ticket release.
        val stillPartial = lastOpen.copy(shares = lastOpen.shares.map { it.copy(paidAmount = 30_000L, status = "paid") })
        val paid = stillPartial.copy(order = order("paid", 90_000L))
        val splits = FakeSplitBills().apply {
            bills += Result.success(lastOpen); bills += Result.success(stillPartial); bills += Result.success(paid)
        }
        val orders = FakePosOrders().apply { results += cashOk(30_000L, 0L, openDrawer = false) }
        val h = harness(splits, orders)
        h.vm.open("ord-1"); advanceUntilIdle()

        h.vm.payShareCash("sp-3", 30_000L, "drw-1"); advanceUntilIdle()
        assertTrue("no settlement before the server says paid", h.effects.none { it is SplitSettlementEffect.BillSettled })

        h.vm.checkQrisStatus(); advanceUntilIdle()
        val settled = h.effects.filterIsInstance<SplitSettlementEffect.BillSettled>().single()
        assertEquals("paid", settled.order.paymentStatus)
    }

    @Test
    fun networkFailure_reusesIdempotencyKey_otherFailuresDoNot() = runTest {
        val splits = FakeSplitBills().apply { bills += Result.success(divided) }
        val orders = FakePosOrders().apply {
            results += Result.failure(DomainException(DomainError.NetworkUnavailable("offline")))
            results += Result.failure(DomainException(DomainError.ServerError("boom", 500)))
            results += Result.failure(DomainException(DomainError.ServerError("boom", 500)))
        }
        val h = harness(splits, orders)
        h.vm.open("ord-1"); advanceUntilIdle()

        repeat(3) { h.vm.payShareQris("sp-2", "drw-1"); advanceUntilIdle() }

        val keys = orders.inputs.map { it.idempotencyKey }
        assertEquals("retry after an unknown outcome keeps the key", keys[0], keys[1])
        assertNotEquals("a definitive failure starts a new attempt", keys[1], keys[2])
        assertEquals(SplitMessage.Server("boom"), h.vm.uiState.value.message)
    }

    @Test
    fun splitNotAllowedConflict_reloadsTheBill() = runTest {
        val splits = FakeSplitBills().apply {
            bills += Result.success(undivided); bills += Result.success(divided)
            divideResult = Result.failure(DomainException(DomainError.Conflict("Sudah dibagi.", SplitBillRepository.CODE_SPLIT_NOT_ALLOWED)))
        }
        val h = harness(splits, FakePosOrders())
        h.vm.open("ord-1"); advanceUntilIdle()

        h.vm.divide(SplitPlan.Equal(3)); advanceUntilIdle()

        assertEquals(2, splits.getBillCalls)
        assertEquals(SplitStage.SETTLE, h.vm.uiState.value.stage)
        assertEquals(3, h.vm.uiState.value.bill!!.shares.size)
    }
}
