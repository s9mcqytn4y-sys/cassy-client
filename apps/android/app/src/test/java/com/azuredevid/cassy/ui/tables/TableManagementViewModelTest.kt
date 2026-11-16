package com.azuredevid.cassy.ui.tables

import androidx.datastore.core.DataStore
import com.azuredevid.cassy.R
import com.azuredevid.cassy.core.hardware.PrinterPort9100Manager
import com.azuredevid.cassy.core.hardware.TableQrPosterCopy
import com.azuredevid.cassy.core.security.DataStoreSessionManager
import com.azuredevid.cassy.data.repository.ServerSettingsRepository
import com.azuredevid.cassy.domain.error.DomainError
import com.azuredevid.cassy.domain.error.DomainException
import com.azuredevid.cassy.domain.model.StaffIdentity
import com.azuredevid.cassy.domain.model.StoredSession
import com.azuredevid.cassy.domain.model.TableDraft
import com.azuredevid.cassy.domain.model.TableEntry
import com.azuredevid.cassy.domain.model.TableStatus
import com.azuredevid.cassy.domain.repository.PrinterConfig
import com.azuredevid.cassy.domain.repository.PrinterSettingsRepository
import com.azuredevid.cassy.domain.repository.TableManagementRepository
import com.azuredevid.cassy.ui.designsystem.CassyToastTone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

@OptIn(ExperimentalCoroutinesApi::class)
class TableManagementViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private class InMemorySessionDataStore : DataStore<StoredSession> {
        private val state = MutableStateFlow(StoredSession())
        private val mutex = Mutex()
        override val data: Flow<StoredSession> = state
        override suspend fun updateData(transform: suspend (t: StoredSession) -> StoredSession): StoredSession =
            mutex.withLock { transform(state.value).also { state.value = it } }
    }

    private class FakeServerSettings : ServerSettingsRepository {
        override fun getBaseUrl(): String = "https://demo.cassy.test"
        override fun setBaseUrl(url: String) = Unit
    }

    private class FakePrinterSettings(var config: PrinterConfig = PrinterConfig()) : PrinterSettingsRepository {
        override val configFlow: Flow<PrinterConfig> = MutableStateFlow(config)
        override suspend fun getConfig(): PrinterConfig = config
        override suspend fun saveConfig(config: PrinterConfig) { this.config = config }
        override suspend fun clearConfig() { config = PrinterConfig() }
    }

    private class FakeTableRepository : TableManagementRepository {
        var tables = mutableListOf(table("t-1", TableStatus.AVAILABLE), table("t-2", TableStatus.OCCUPIED, session = true))
        var failList = false
        var createError: Throwable? = null
        var archiveError: Throwable? = null
        var rotateCalls = 0
        var createCalls = 0

        override suspend fun listTables(): Result<List<TableEntry>> =
            if (failList) Result.failure(DomainException(DomainError.NetworkUnavailable("Koneksi terputus")))
            else Result.success(tables.toList())

        override suspend fun createTable(draft: TableDraft): Result<TableEntry> {
            createCalls++
            createError?.let { return Result.failure(it) }
            return Result.success(table("t-new", TableStatus.AVAILABLE).copy(tableNumber = draft.tableNumber))
        }

        override suspend fun updateTable(id: String, draft: TableDraft): Result<TableEntry> =
            Result.success(tables.first { it.id == id }.copy(tableNumber = draft.tableNumber))

        override suspend fun archiveTable(id: String): Result<Unit> =
            archiveError?.let { Result.failure(it) } ?: Result.success(Unit)

        override suspend fun rotateQr(id: String): Result<TableEntry> {
            rotateCalls++
            return Result.success(tables.first { it.id == id }.copy(qrToken = "rotated-token", qrRotatedAt = "2026-11-16T10:00:00Z"))
        }
    }

    private lateinit var repo: FakeTableRepository
    private lateinit var printerSettings: FakePrinterSettings
    private val effects = mutableListOf<TableManagementUiEffect>()
    private val copy = TableQrPosterCopy("Meja", "Pindai untuk pesan", "")

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repo = FakeTableRepository()
        printerSettings = FakePrinterSettings()
        effects.clear()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun TestScope.viewModel(
        permissions: List<String>? = listOf("table.manage", "table.qr.manage")
    ): TableManagementViewModel {
        val session = DataStoreSessionManager(InMemorySessionDataStore(), backgroundScope)
        if (permissions != null) {
            session.saveLoginSession(
                accessToken = "jwt",
                accessTokenExpiresAt = "2026-11-17T00:00:00Z",
                sessionExpiresAt = "2026-11-17T00:00:00Z",
                staff = StaffIdentity("u1", "Pemilik", "owner@cassy.test", "owner", "s1", "kopi", "Kopi Nusantara"),
                permissions = permissions
            )
        }
        val vm = TableManagementViewModel(
            repository = repo,
            sessionManager = session,
            serverSettings = FakeServerSettings(),
            printerSettings = printerSettings,
            printerManager = PrinterPort9100Manager(ioDispatcher = testDispatcher),
            ioDispatcher = testDispatcher
        )
        // Unconfined: background work is not run by advanceUntilIdle, so the collector must resume on emit.
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.effects.collect { effects += it } }
        testDispatcher.scheduler.advanceUntilIdle()
        return vm
    }

    private fun lastMessage(): TableManagementUiEffect.ShowMessage =
        effects.filterIsInstance<TableManagementUiEffect.ShowMessage>().last()

    @Test
    fun noSession_failsClosedWithNothingLoadedAndNoActions() = runTest(testDispatcher) {
        val vm = viewModel(permissions = null)

        val state = vm.uiState.value
        assertTrue(state.tables.isEmpty())
        assertFalse(state.canManageTables)
        assertFalse(state.canManageQr)
        assertEquals(TableMessage.Res(R.string.tables_error_no_session), state.loadError)

        vm.saveTable(null, TableDraft("9"))
        vm.confirmRotateQr("t-1")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, repo.createCalls)
        assertEquals(0, repo.rotateCalls)
    }

    @Test
    fun withoutQrPermission_qrStaysHiddenAndRotationIsIgnored() = runTest(testDispatcher) {
        val vm = viewModel(permissions = listOf("table.manage"))
        vm.selectTable("t-1")

        assertTrue(vm.uiState.value.canManageTables)
        assertFalse(vm.uiState.value.canManageQr)
        assertNull(vm.uiState.value.selectedQrUrl)

        vm.confirmRotateQr("t-1")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, repo.rotateCalls)
    }

    @Test
    fun selectedFreeTable_exposesGuestOrderUrl() = runTest(testDispatcher) {
        val vm = viewModel()
        vm.selectTable("t-1")
        assertEquals("https://demo.cassy.test/order/tok-t-1", vm.uiState.value.selectedQrUrl)
    }

    @Test
    fun rotate_reloadsBoardAndRefusesWhenGuestSeatedSinceLastLoad() = runTest(testDispatcher) {
        val vm = viewModel()
        assertEquals(TableStatus.AVAILABLE, vm.uiState.value.tables.first { it.id == "t-1" }.status)

        // A guest scans the sticker after the screen loaded.
        repo.tables[0] = repo.tables[0].copy(status = TableStatus.OCCUPIED, hasOpenSession = true)
        vm.confirmRotateQr("t-1")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, repo.rotateCalls)
        assertEquals(TableMessage.Res(R.string.tables_block_active_session), lastMessage().message)
        assertEquals(CassyToastTone.WARNING, lastMessage().tone)
        assertEquals("cached board refreshed", TableStatus.OCCUPIED, vm.uiState.value.tables.first { it.id == "t-1" }.status)
        assertFalse(vm.uiState.value.isSubmitting)
    }

    @Test
    fun rotate_refusesWhenFreshStateCannotBeLoaded() = runTest(testDispatcher) {
        val vm = viewModel()
        repo.failList = true

        vm.confirmRotateQr("t-1")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, repo.rotateCalls)
        assertEquals(TableMessage.Res(R.string.tables_block_unknown), lastMessage().message)
    }

    @Test
    fun rotate_refusesWhenTableVanishedFromBoard() = runTest(testDispatcher) {
        val vm = viewModel()
        repo.tables.removeAt(0)

        vm.confirmRotateQr("t-1")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, repo.rotateCalls)
        assertEquals(TableMessage.Res(R.string.tables_block_unknown), lastMessage().message)
    }

    @Test
    fun rotate_onFreeTableReplacesToken() = runTest(testDispatcher) {
        val vm = viewModel()

        vm.confirmRotateQr("t-1")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, repo.rotateCalls)
        assertEquals("rotated-token", vm.uiState.value.tables.first { it.id == "t-1" }.qrToken)
        assertEquals(CassyToastTone.SUCCESS, lastMessage().tone)
    }

    @Test
    fun saveTable_planLimitShowsServerTextAndKeepsFormOpen() = runTest(testDispatcher) {
        val vm = viewModel()
        repo.createError = DomainException(DomainError.ServerError("Paket Anda mencakup maksimal 15 meja aktif.", 402))

        vm.saveTable(null, TableDraft("16"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(TableMessage.Server("Paket Anda mencakup maksimal 15 meja aktif."), lastMessage().message)
        assertFalse(effects.any { it is TableManagementUiEffect.FormSaved })
        assertFalse(vm.uiState.value.isSubmitting)
    }

    @Test
    fun saveTable_successAddsAndSelectsTable() = runTest(testDispatcher) {
        val vm = viewModel()

        vm.saveTable(null, TableDraft("9"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(effects.any { it is TableManagementUiEffect.FormSaved })
        assertEquals("t-new", vm.uiState.value.selectedTableId)
        assertEquals(3, vm.uiState.value.tables.size)
    }

    @Test
    fun archive_conflictKeepsTableAndShowsBackendReason() = runTest(testDispatcher) {
        val vm = viewModel()
        repo.archiveError = DomainException(DomainError.Conflict("Meja masih memiliki sesi aktif.", "TABLE_SESSION_OPEN"))

        vm.archiveTable("t-2")
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(vm.uiState.value.tables.firstOrNull { it.id == "t-2" })
        assertEquals(TableMessage.Server("Meja masih memiliki sesi aktif."), lastMessage().message)
    }

    @Test
    fun printPoster_withoutCashierPrinterWarnsAndSendsNothing() = runTest(testDispatcher) {
        val vm = viewModel()

        vm.printPoster("t-1", copy)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(TableMessage.Res(R.string.tables_error_no_printer), lastMessage().message)
        assertFalse(vm.uiState.value.isPrinting)
    }

    @Test
    fun printPoster_sendsEscPosWithGuestUrlToCashierPrinter() = runTest(testDispatcher) {
        val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        var received = ByteArray(0)
        val reader = thread { server.accept().use { received = it.getInputStream().readBytes() } }
        printerSettings.config = PrinterConfig(cashierPrinterIp = "127.0.0.1", cashierPrinterPort = server.localPort)
        val vm = viewModel()

        vm.printPoster("t-1", copy)
        testDispatcher.scheduler.advanceUntilIdle()
        reader.join(5_000)
        server.close()

        val text = String(received, Charsets.ISO_8859_1)
        assertTrue(text.contains("https://demo.cassy.test/order/tok-t-1"))
        assertTrue(text.contains("Kopi Nusantara"))
        assertEquals(CassyToastTone.SUCCESS, lastMessage().tone)
        assertFalse(vm.uiState.value.isPrinting)
    }

    private companion object {
        fun table(id: String, status: TableStatus, session: Boolean = false) = TableEntry(
            id = id, tableNumber = id.removePrefix("t-"), label = "", section = "", capacity = 4,
            qrToken = "tok-$id", qrRotatedAt = "2026-11-10T08:00:00Z", isActive = true, status = status,
            hasOpenSession = session, mergedIntoTableId = null, unpaidOrders = 0
        )
    }
}
