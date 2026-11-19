package com.azuredevid.cassy.ui.settings

import com.azuredevid.cassy.domain.repository.CreatedDisplayScreen
import com.azuredevid.cassy.domain.repository.DisplayScreen
import com.azuredevid.cassy.domain.repository.DisplayTokenRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DisplayScreensViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private class FakeTokens : DisplayTokenRepository {
        val screens = mutableListOf(DisplayScreen("d-1", "TV lama", "2026-11-10T08:00:00Z", revokedAt = "2026-11-18T09:00:00Z"))
        var revoked: String? = null

        override suspend fun list() = Result.success(screens.toList())

        override suspend fun create(name: String): Result<CreatedDisplayScreen> {
            val screen = DisplayScreen("d-2", name, "2026-11-19T08:00:00Z")
            screens += screen
            return Result.success(CreatedDisplayScreen(screen, "s.d-2.x", "https://web/antrean#t=s.d-2.x"))
        }

        override suspend fun revoke(id: String): Result<Unit> {
            revoked = id
            screens.replaceAll { if (it.id == id) it.copy(revokedAt = "2026-11-19T09:00:00Z") else it }
            return Result.success(Unit)
        }
    }

    @Test
    fun createShowsTheLinkOnceAndListsActiveScreensFirst() = runTest {
        val vm = DisplayScreensViewModel(FakeTokens())
        vm.load()
        vm.openNew()
        vm.setName("  TV teras ")
        vm.create()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("https://web/antrean#t=s.d-2.x", vm.uiState.value.created?.displayUrl)
        assertNull(vm.uiState.value.newName)
        assertEquals(listOf("d-2", "d-1"), vm.uiState.value.screens.map { it.id })
        vm.dismissCreated()
        assertNull(vm.uiState.value.created)
    }

    @Test
    fun revokeNeedsConfirmationAndSkipsRevokedScreens() = runTest {
        val tokens = FakeTokens()
        tokens.screens += DisplayScreen("d-3", "TV kasir", "2026-11-19T08:00:00Z")
        val vm = DisplayScreensViewModel(tokens)
        vm.load()
        dispatcher.scheduler.advanceUntilIdle()

        vm.askRevoke(tokens.screens.first { it.id == "d-1" })
        assertNull(vm.uiState.value.revoking)

        vm.askRevoke(tokens.screens.first { it.id == "d-3" })
        vm.confirmRevoke()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("d-3", tokens.revoked)
        assertNull(vm.uiState.value.revoking)
        assertEquals(true, vm.uiState.value.messageOk)
    }
}
