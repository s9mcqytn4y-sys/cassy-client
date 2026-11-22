package com.azuredevid.cassy.ui.queue

import com.azuredevid.cassy.domain.repository.PublicQueueEntry
import com.azuredevid.cassy.domain.repository.PublicQueueRepository
import com.azuredevid.cassy.domain.repository.PublicQueueSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TvQueueDisplayViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private class FakePublicQueueRepository(
        private val result: Result<PublicQueueSnapshot>
    ) : PublicQueueRepository {
        var lastToken: String? = null
        override suspend fun fetchQueue(displayToken: String): Result<PublicQueueSnapshot> {
            lastToken = displayToken
            return result
        }

        override suspend fun requestWsTicket(displayToken: String): Result<String> = Result.success("ticket-1")
    }

    @Test
    fun pairAndStartPartitionsPreparingAndReadyNumbers() = runTest {
        val repo = FakePublicQueueRepository(
            Result.success(
                PublicQueueSnapshot(
                    storeName = "Cassy Roastery Senopati",
                    storeTimezone = "Asia/Jakarta",
                    orders = listOf(
                        PublicQueueEntry("A001", "confirmed"),
                        PublicQueueEntry("A002", "preparing"),
                        PublicQueueEntry("A003", "ready"),
                        PublicQueueEntry("A004", "served")
                    )
                )
            )
        )
        val vm = TvQueueDisplayViewModel(repo, pollIntervalMs = 60_000L)
        vm.setTokenInput("  demo-display-token  ")
        vm.pairAndStart()
        dispatcher.scheduler.runCurrent()

        assertEquals("demo-display-token", repo.lastToken)
        assertTrue(vm.uiState.value.isPaired)
        assertEquals("Cassy Roastery Senopati", vm.uiState.value.storeName)
        assertEquals(listOf("A001", "A002"), vm.uiState.value.preparingNumbers)
        assertEquals(listOf("A003"), vm.uiState.value.readyNumbers)

        vm.disconnect()
        assertFalse(vm.uiState.value.isPaired)
        assertTrue(vm.uiState.value.preparingNumbers.isEmpty())
    }
}
