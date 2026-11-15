package com.azuredevid.cassy.ui.pairing

import com.azuredevid.cassy.domain.model.StaffIdentity
import com.azuredevid.cassy.domain.repository.AuthLoginResult
import com.azuredevid.cassy.domain.repository.AuthRepository
import com.azuredevid.cassy.domain.repository.MobileBootstrapData
import com.azuredevid.cassy.domain.repository.ServerConnectionStatus
import com.azuredevid.cassy.domain.repository.ServerConnectivityState
import com.azuredevid.cassy.domain.repository.ServerHealthMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StorePairingViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private class FakeAuthRepository : AuthRepository {
        var shouldFail = false
        var lastStoreSlug: String? = null
        var lastEmail: String? = null
        var lastPin: String? = null
        var lastDeviceCred: String? = null

        override suspend fun login(
            storeSlug: String,
            email: String,
            badge: String,
            pin: String,
            deviceCredential: String?
        ): Result<AuthLoginResult> {
            lastStoreSlug = storeSlug
            lastEmail = email
            lastPin = pin
            lastDeviceCred = deviceCredential
            if (shouldFail) return Result.failure(IllegalStateException("PIN salah"))
            return Result.success(
                AuthLoginResult(
                    staff = StaffIdentity("u1", "Budi", email, "owner", "s1", storeSlug, "Kopi Senja"),
                    pinChangeRequired = false,
                    offlinePinVerifier = null
                )
            )
        }

        override suspend fun fetchMobileBootstrap(): Result<MobileBootstrapData> =
            Result.success(MobileBootstrapData(storeId = "s1", storeSlug = lastStoreSlug.orEmpty(), storeName = "Kopi Senja"))

        override suspend fun fetchMe(): Result<StaffIdentity> =
            Result.failure(UnsupportedOperationException())

        override suspend fun logout(): Result<Unit> = Result.success(Unit)
    }

    private class FakeServerHealthMonitor : ServerHealthMonitor {
        val statusFlow = MutableStateFlow(ServerConnectivityState(ServerConnectionStatus.CHECKING))
        var probeRequestCount = 0

        override fun observeConnectivity(): Flow<ServerConnectivityState> = statusFlow

        override fun requestProbe() {
            probeRequestCount++
            statusFlow.value = ServerConnectivityState(ServerConnectionStatus.ONLINE, latencyMs = 18L)
        }

        override suspend fun probeNow(): ServerConnectivityState = statusFlow.value
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_hasNoHardcodedStoreSlug() {
        val vm = StorePairingViewModel(FakeAuthRepository(), FakeServerHealthMonitor())
        assertEquals("", vm.uiState.value.storeSlug)
        assertEquals("", vm.uiState.value.email)
        assertEquals("", vm.uiState.value.pin)
        assertFalse(vm.uiState.value.isPinVisible)
    }

    @Test
    fun submitWithEmptyFields_setsInlineValidationErrors() {
        val vm = StorePairingViewModel(FakeAuthRepository(), FakeServerHealthMonitor())
        vm.onEvent(StorePairingUiEvent.SubmitLogin)
        assertEquals(StorePairingViewModel.ERR_SLUG_REQUIRED, vm.uiState.value.storeSlugError)
        assertEquals(StorePairingViewModel.ERR_EMAIL_REQUIRED, vm.uiState.value.emailError)
        assertEquals(StorePairingViewModel.ERR_PIN_INVALID, vm.uiState.value.pinError)
    }

    @Test
    fun submitWithInvalidEmailFormat_setsEmailInvalidError() {
        val vm = StorePairingViewModel(FakeAuthRepository(), FakeServerHealthMonitor())
        vm.onEvent(StorePairingUiEvent.UpdateStoreSlug("Kopi Senja!!"))
        vm.onEvent(StorePairingUiEvent.UpdateEmail("not-an-email"))
        vm.onEvent(StorePairingUiEvent.UpdatePin("482913"))
        vm.onEvent(StorePairingUiEvent.SubmitLogin)

        assertEquals("kopisenja", vm.uiState.value.storeSlug)
        assertNull(vm.uiState.value.storeSlugError)
        assertEquals(StorePairingViewModel.ERR_EMAIL_INVALID, vm.uiState.value.emailError)
        assertNull(vm.uiState.value.pinError)
    }

    @Test
    fun validLoginAndBootstrap_emitsPairingSucceededAndClearsPinFromState() = runTest {
        val repo = FakeAuthRepository()
        val vm = StorePairingViewModel(repo, FakeServerHealthMonitor())

        vm.onEvent(StorePairingUiEvent.UpdateStoreSlug("Kopi-Senja"))
        vm.onEvent(StorePairingUiEvent.UpdateEmail("budi@kopi.id"))
        vm.onEvent(StorePairingUiEvent.UpdatePin("482913"))
        vm.onEvent(StorePairingUiEvent.SubmitLogin)

        val effect = vm.effects.first()
        assertTrue(effect is StorePairingUiEffect.PairingSucceeded)
        val succeeded = effect as StorePairingUiEffect.PairingSucceeded
        assertEquals("kopi-senja", succeeded.storeSlug)
        assertEquals("Kopi Senja", succeeded.storeName)
        assertEquals("482913", repo.lastPin)
        assertEquals("", vm.uiState.value.pin)
        assertNull(vm.uiState.value.formError)
    }

    @Test
    fun qrPayload_populatesStoreSlugEmailBadgeAndDeviceCredential() {
        val vm = StorePairingViewModel(FakeAuthRepository(), FakeServerHealthMonitor())
        val payload = """{"store_slug":"kopi-senja","email":"kasir@kopi.id","badge":"K01","pairing_token":"tok-123"}"""

        vm.onEvent(StorePairingUiEvent.HandleQrPayload(payload))

        val state = vm.uiState.value
        assertEquals("kopi-senja", state.storeSlug)
        assertEquals("kasir@kopi.id", state.email)
        assertEquals("K01", state.badge)
        assertEquals("tok-123", state.deviceCredential)
        assertNotNull(state.qrScannedInfo)
        assertNull(state.formError)
    }

    @Test
    fun hidScannerSubmit_appliesPayloadAndPermissionDeniedShowsBanner() {
        val vm = StorePairingViewModel(FakeAuthRepository(), FakeServerHealthMonitor())

        vm.onEvent(StorePairingUiEvent.CameraPermissionDenied)
        assertTrue(vm.uiState.value.cameraPermissionDenied)

        vm.onEvent(StorePairingUiEvent.OpenHidScanner(true))
        assertTrue(vm.uiState.value.isHidScannerOpen)

        vm.onEvent(
            StorePairingUiEvent.UpdateHidInput(
                """{"store_slug":"warung-pandan","email":"kasir@pandan.id","device_credential":"dev-99"}"""
            )
        )
        vm.onEvent(StorePairingUiEvent.SubmitHidQrPayload)

        assertFalse(vm.uiState.value.isHidScannerOpen)
        assertFalse(vm.uiState.value.cameraPermissionDenied)
        assertEquals("warung-pandan", vm.uiState.value.storeSlug)
        assertEquals("kasir@pandan.id", vm.uiState.value.email)
        assertEquals("dev-99", vm.uiState.value.deviceCredential)

        vm.onEvent(StorePairingUiEvent.TogglePinVisibility)
        assertTrue(vm.uiState.value.isPinVisible)
    }

    @Test
    fun connectivityState_forwardsHealthMonitorUpdatesAndRetry() = runTest {
        val monitor = FakeServerHealthMonitor()
        val vm = StorePairingViewModel(FakeAuthRepository(), monitor)

        val collectorJob = launch { vm.connectivityState.collect {} }
        advanceUntilIdle()

        assertEquals(ServerConnectionStatus.CHECKING, vm.connectivityState.value.status)

        vm.onEvent(StorePairingUiEvent.RetryServerHealthProbe)
        advanceUntilIdle()

        assertEquals(1, monitor.probeRequestCount)
        assertEquals(ServerConnectionStatus.ONLINE, vm.connectivityState.value.status)
        assertEquals(18L, vm.connectivityState.value.latencyMs)

        collectorJob.cancel()
    }
}
