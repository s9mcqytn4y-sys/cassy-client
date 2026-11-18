package com.azuredevid.cassy.ui.pos

import com.azuredevid.cassy.domain.error.DomainError
import com.azuredevid.cassy.domain.error.DomainException
import com.azuredevid.cassy.domain.model.CreatePosOrderItemInput
import com.azuredevid.cassy.domain.model.PosOrder
import com.azuredevid.cassy.domain.model.PosOrderItem
import com.azuredevid.cassy.domain.repository.ApprovalRepository
import com.azuredevid.cassy.domain.repository.ClosedTab
import com.azuredevid.cassy.domain.repository.OwnerApproval
import com.azuredevid.cassy.domain.repository.PosTable
import com.azuredevid.cassy.domain.repository.TabItemVoidInput
import com.azuredevid.cassy.domain.repository.TableTabRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TabItemVoidViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** Voids up to [cashierLimit] units on the cashier PIN alone, like the server's O1 threshold. */
    private class FakeTabs(private val cashierLimit: Int) : TableTabRepository {
        val calls = mutableListOf<TabItemVoidInput>()

        override suspend fun voidItem(orderId: String, itemId: String, input: TabItemVoidInput): Result<PosOrder> {
            calls += input
            if (input.quantity > cashierLimit && input.ownerPin == null && input.approvalId == null) {
                return Result.failure(DomainException(DomainError.Validation("owner", code = "OWNER_PIN_REQUIRED")))
            }
            return Result.success(PosOrder(id = orderId, orderNumber = "T-1"))
        }

        override suspend fun listTables(): Result<List<PosTable>> = error("unused")
        override suspend fun openTab(idempotencyKey: String, tableId: String, tableNumber: String): Result<PosOrder> = error("unused")
        override suspend fun submitRound(orderId: String, idempotencyKey: String, items: List<CreatePosOrderItemInput>): Result<Unit> = error("unused")
        override suspend fun getTab(orderId: String): Result<PosOrder> = error("unused")
        override suspend fun closeTab(orderId: String): Result<ClosedTab> = error("unused")
    }

    private class FakeApprovals(private val statuses: ArrayDeque<String>) : ApprovalRepository {
        var requestedQuantity: Int? = null

        override suspend fun requestVoidItem(itemId: String, quantity: Int, reason: String): Result<OwnerApproval> {
            requestedQuantity = quantity
            return Result.success(OwnerApproval("ap-1", "pending"))
        }

        override suspend fun get(id: String): Result<OwnerApproval> = Result.success(OwnerApproval(id, statuses.removeFirst()))
    }

    private val line = PosOrderItem(id = "line-1", productId = "p-1", snapshotName = "Croissant", quantity = 3)

    private fun TabItemVoidViewModel.fillForm(quantity: Int) {
        open("tab-1", line, "Croissant")
        setQuantity(quantity)
        setReason("tamu batal")
        setPin("482913")
    }

    @Test
    fun smallVoidUsesTheCashierPinAndClosesTheDialog() = runTest {
        val tabs = FakeTabs(cashierLimit = 1)
        val vm = TabItemVoidViewModel(tabs, FakeApprovals(ArrayDeque()), pollIntervalMs = 10)
        vm.fillForm(1)

        vm.submit()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("Croissant", vm.voided.first().itemName)
        assertFalse(vm.uiState.value.isOpen)
        assertNull(tabs.calls.single().ownerPin)
    }

    @Test
    fun aboveTheLimitAsksTheOwnerAndVoidsWithTheApproval() = runTest {
        val tabs = FakeTabs(cashierLimit = 1)
        val approvals = FakeApprovals(ArrayDeque(listOf("pending", "approved")))
        val vm = TabItemVoidViewModel(tabs, approvals, pollIntervalMs = 10)
        vm.fillForm(2)

        vm.submit()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(TabVoidStep.OWNER, vm.uiState.value.step)

        vm.requestOwnerApproval()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, approvals.requestedQuantity)
        assertEquals("ap-1", tabs.calls.last().approvalId)
        assertEquals(2, tabs.calls.last().quantity)
        assertFalse(vm.uiState.value.isOpen)
    }

    @Test
    fun aRejectedRequestReturnsToTheOwnerStepWithoutVoiding() = runTest {
        val tabs = FakeTabs(cashierLimit = 1)
        val vm = TabItemVoidViewModel(tabs, FakeApprovals(ArrayDeque(listOf("rejected"))), pollIntervalMs = 10)
        vm.fillForm(3)
        vm.submit()
        dispatcher.scheduler.advanceUntilIdle()

        vm.requestOwnerApproval()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(TabVoidStep.OWNER, vm.uiState.value.step)
        assertEquals(1, tabs.calls.size)
    }

    @Test
    fun theQuantityIsFrozenWhileWaitingForTheOwner() = runTest {
        val vm = TabItemVoidViewModel(FakeTabs(cashierLimit = 0), FakeApprovals(ArrayDeque(listOf("pending"))), pollIntervalMs = 1_000_000)
        vm.fillForm(2)
        vm.submit()
        dispatcher.scheduler.advanceUntilIdle()
        vm.requestOwnerApproval()
        dispatcher.scheduler.runCurrent()

        vm.setQuantity(3)

        assertEquals(TabVoidStep.WAITING, vm.uiState.value.step)
        assertEquals(2, vm.uiState.value.quantity)
        vm.dismiss()
    }
}
