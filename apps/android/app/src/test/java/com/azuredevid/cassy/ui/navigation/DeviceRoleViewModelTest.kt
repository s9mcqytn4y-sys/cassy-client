package com.azuredevid.cassy.ui.navigation

import com.azuredevid.cassy.domain.repository.DeviceProfile
import com.azuredevid.cassy.domain.repository.DeviceProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceRoleViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun profiles(role: String?) = object : DeviceProfileRepository {
        override suspend fun profile() =
            role?.let { Result.success(DeviceProfile(it)) } ?: Result.failure(IllegalStateException("unpaired"))
    }

    @Test
    fun kdsDeviceWithAnOpenShiftIsHeldUntilTheShiftCloses() = runTest {
        var shiftOpen = true
        val vm = DeviceRoleViewModel(profiles("kds"), { shiftOpen }, { 0 }, recheckMs = 1_000)

        vm.refresh()
        dispatcher.scheduler.runCurrent()
        assertEquals(DeviceRoleUiState(DeviceHome.KDS, held = true), vm.uiState.value)

        shiftOpen = false
        dispatcher.scheduler.advanceTimeBy(1_001)
        dispatcher.scheduler.runCurrent()
        assertEquals(DeviceRoleUiState(DeviceHome.KDS, held = false), vm.uiState.value)
    }

    @Test
    fun unsyncedOfflineOrdersAlsoHoldTheSwitch() = runTest {
        var pending = 2
        val vm = DeviceRoleViewModel(profiles("kds"), { false }, { pending }, recheckMs = 1_000)
        vm.refresh()
        dispatcher.scheduler.runCurrent()
        assertEquals(true, vm.uiState.value.held)
        // A held device rechecks forever; clearing the queue ends the loop so runTest can finish.
        pending = 0
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(false, vm.uiState.value.held)
    }

    @Test
    fun otherRolesLiveOnTheCashierAndAnUnpairedTabletStaysPut() = runTest {
        val backoffice = DeviceRoleViewModel(profiles("backoffice"), { true }, { 5 })
        backoffice.refresh()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(DeviceRoleUiState(DeviceHome.POS), backoffice.uiState.value)

        val unpaired = DeviceRoleViewModel(profiles(null), { false }, { 0 })
        unpaired.refresh()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(DeviceRoleUiState(), unpaired.uiState.value)
    }

    @Test
    fun splitBillFollowsTheOwnerFlagAndDefaultsOn() = runTest {
        val off = object : DeviceProfileRepository {
            override suspend fun profile() = Result.success(DeviceProfile("pos", mapOf("split_bill" to false)))
        }
        val vm = DeviceRoleViewModel(off, { false }, { 0 })
        vm.refresh()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(false, vm.uiState.value.splitBillEnabled)

        val unset = DeviceRoleViewModel(profiles("pos"), { false }, { 0 })
        unset.refresh()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(true, unset.uiState.value.splitBillEnabled)
    }
}
