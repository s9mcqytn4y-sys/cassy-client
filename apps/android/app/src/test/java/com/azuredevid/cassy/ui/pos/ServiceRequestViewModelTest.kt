package com.azuredevid.cassy.ui.pos

import com.azuredevid.cassy.domain.repository.ServiceRequest
import com.azuredevid.cassy.domain.repository.ServiceRequestRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ServiceRequestViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private class FakeRepo(var queue: MutableList<ServiceRequest>) : ServiceRequestRepository {
        var failNext = false
        var listCalls = 0

        override suspend fun listActive(): Result<List<ServiceRequest>> {
            listCalls++
            return Result.success(queue.toList())
        }

        override suspend fun acknowledge(id: String) = move(id, "acknowledged")

        override suspend fun markDone(id: String) = move(id, "done")

        override suspend fun cancelAndMute(id: String) = move(id, "cancelled")

        private fun move(id: String, status: String): Result<ServiceRequest> {
            if (failNext) {
                failNext = false
                queue.removeAll { it.id == id }
                return Result.failure(IllegalStateException("sudah ditangani"))
            }
            val updated = queue.first { it.id == id }.copy(status = status)
            queue.replaceAll { if (it.id == id) updated else it }
            if (status == "done" || status == "cancelled") queue.removeAll { it.id == id }
            return Result.success(updated)
        }
    }

    private fun request(id: String, table: String) =
        ServiceRequest(id, table, "Minta bill", null, "open", "2026-11-19T08:0$id:00Z")

    @Test
    fun takeKeepsTheRequestAndDoneRemovesIt() = runTest {
        val repo = FakeRepo(mutableListOf(request("1", "07"), request("2", "03")))
        val vm = ServiceRequestViewModel(repo)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, vm.uiState.value.requests.size)

        vm.acknowledge("1")
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.requests.first { it.id == "1" }.isAcknowledged)

        vm.markDone("1")
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf("2"), vm.uiState.value.requests.map { it.id })
        assertNull(vm.uiState.value.busyRequestId)

        vm.cancelAndMute("2")
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.requests.isEmpty())
    }

    @Test
    fun aFailedUpdateReloadsTheServerQueue() = runTest {
        val repo = FakeRepo(mutableListOf(request("1", "07")))
        val vm = ServiceRequestViewModel(repo)
        dispatcher.scheduler.advanceUntilIdle()

        repo.failNext = true
        vm.markDone("1")
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.uiState.value.requests.isEmpty())
        assertEquals(2, repo.listCalls)
    }
}
