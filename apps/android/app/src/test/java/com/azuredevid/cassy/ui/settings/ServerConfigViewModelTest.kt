package com.azuredevid.cassy.ui.settings

import androidx.lifecycle.SavedStateHandle
import com.azuredevid.cassy.data.repository.ServerSettingsRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
class ServerConfigViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

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
        val okClient = HttpClient(MockEngine { request ->
            requestedPath = request.url.encodedPath
            respond(
                content = """{"status":"ok"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        })

        val okVm = ServerConfigViewModel(repo, httpClient = okClient)
        val effectDeferred = async(start = CoroutineStart.UNDISPATCHED) { okVm.effects.first() }
        okVm.testConnection()
        val capturedEffect = effectDeferred.await()

        assertEquals("/api/v1/health", requestedPath)
        assertEquals(true, okVm.uiState.value.probeStatusOk)
        assertEquals(200, okVm.uiState.value.probeHttpCode)
        assertTrue((capturedEffect as? ServerConfigUiEffect.ShowProbeResult)?.success == true)

        val failClient = HttpClient(MockEngine {
            respond(
                content = "unavailable",
                status = HttpStatusCode.ServiceUnavailable
            )
        })
        val failVm = ServerConfigViewModel(repo, httpClient = failClient)
        val failEffectDeferred = async(start = CoroutineStart.UNDISPATCHED) { failVm.effects.first() }
        failVm.testConnection()
        failEffectDeferred.await()

        assertEquals(false, failVm.uiState.value.probeStatusOk)
        assertEquals(503, failVm.uiState.value.probeHttpCode)
    }
}
