package com.azuredevid.cassy.ui.settings

import androidx.lifecycle.SavedStateHandle
import com.azuredevid.cassy.data.repository.ServerSettingsRepository
import com.azuredevid.cassy.domain.repository.ServerConnectionStatus
import com.azuredevid.cassy.domain.repository.ServerConnectivityState
import com.azuredevid.cassy.domain.repository.ServerHealthMonitor
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ServerConfigViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private class FakeServerSettingsRepository(
        private var currentUrl: String = "http://10.0.2.2:8080"
    ) : ServerSettingsRepository {
        override fun getBaseUrl(): String = currentUrl
        override fun setBaseUrl(url: String) {
            currentUrl = url.trim().trimEnd('/')
        }
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
    fun savedStateHandleRestoresEditedUrlAcrossRecreation() = runTest(testDispatcher) {
        val repo = FakeServerSettingsRepository("http://10.0.2.2:8080")
        val handle = SavedStateHandle()
        val firstVm = ServerConfigViewModel(repo, savedStateHandle = handle)
        firstVm.applyPreset("https://demo.abbdullaziz.online")

        val recreatedVm = ServerConfigViewModel(repo, savedStateHandle = handle)
        assertEquals("https://demo.abbdullaziz.online", recreatedVm.uiState.value.urlInput)
    }

    @Test
    fun save_validatesSchemeAndPersistsNormalizedUrl() = runTest(testDispatcher) {
        val repo = FakeServerSettingsRepository()
        val vm = ServerConfigViewModel(repo)

        vm.onUrlChanged("ftp://invalid")
        assertFalse(vm.save())
        assertTrue(vm.uiState.value.isUrlInvalid)

        vm.onUrlChanged("https://demo.abbdullaziz.online/")
        assertTrue(vm.save())
        assertFalse(vm.uiState.value.isUrlInvalid)
        assertTrue(vm.uiState.value.isSaved)
        assertEquals("https://demo.abbdullaziz.online", repo.getBaseUrl())
    }

    @Test
    fun testConnection_successAndFailureProbeHealthEndpoint() = runTest(testDispatcher) {
        val repo = FakeServerSettingsRepository("https://demo.abbdullaziz.online")
        var requestedPath = ""
        val okClient = HttpClient(
            MockEngine(
                MockEngineConfig().apply {
                    dispatcher = testDispatcher
                    addHandler { request ->
                        requestedPath = request.url.encodedPath
                        respond(
                            content = """{"status":"ok"}""",
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                    }
                }
            )
        )

        val okEffects = mutableListOf<ServerConfigUiEffect>()
        val okVm = ServerConfigViewModel(repo, httpClient = okClient)
        val okJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            okVm.effects.collect { okEffects += it }
        }
        okVm.testConnection()
        advanceUntilIdle()
        okJob.cancel()
        okClient.close()

        assertEquals("/api/v1/health", requestedPath)
        assertEquals(true, okVm.uiState.value.probeStatusOk)
        assertEquals(200, okVm.uiState.value.probeHttpCode)
        assertNotNull(okVm.uiState.value.probeLatencyMs)
        assertTrue((okEffects.singleOrNull() as? ServerConfigUiEffect.ShowProbeResult)?.success == true)

        val failClient = HttpClient(
            MockEngine(
                MockEngineConfig().apply {
                    dispatcher = testDispatcher
                    addHandler {
                        respond(
                            content = "unavailable",
                            status = HttpStatusCode.ServiceUnavailable
                        )
                    }
                }
            )
        )
        val failEffects = mutableListOf<ServerConfigUiEffect>()
        val failVm = ServerConfigViewModel(repo, httpClient = failClient)
        val failJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            failVm.effects.collect { failEffects += it }
        }
        failVm.testConnection()
        advanceUntilIdle()
        failJob.cancel()
        failClient.close()

        assertEquals(false, failVm.uiState.value.probeStatusOk)
        assertEquals(503, failVm.uiState.value.probeHttpCode)
        assertTrue((failEffects.singleOrNull() as? ServerConfigUiEffect.ShowProbeResult)?.success == false)
    }

    @Test
    fun strictUrlValidation_cleartextHint_andSaveRequestsHealthProbe() = runTest(testDispatcher) {
        val repo = FakeServerSettingsRepository()
        var probeCount = 0
        val monitor = object : ServerHealthMonitor {
            override fun observeConnectivity(): Flow<ServerConnectivityState> =
                MutableStateFlow(ServerConnectivityState(ServerConnectionStatus.ONLINE))
            override fun requestProbe() {
                probeCount++
            }
            override suspend fun probeNow(): ServerConnectivityState =
                ServerConnectivityState(ServerConnectionStatus.ONLINE)
        }
        val vm = ServerConfigViewModel(repo, healthMonitor = monitor)

        vm.onUrlChanged("https://a")
        assertFalse(vm.save())
        assertTrue(vm.uiState.value.isUrlInvalid)

        vm.onUrlChanged("http://192.168.1.50:8080")
        assertTrue(vm.uiState.value.showCleartextWarning)
        assertTrue(vm.save())
        assertEquals(1, probeCount)

        vm.applyPreset("https://demo.abbdullaziz.online")
        assertFalse(vm.uiState.value.showCleartextWarning)
    }
}
