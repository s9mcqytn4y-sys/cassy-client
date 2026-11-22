package com.azuredevid.cassy.ui.settings

import com.azuredevid.cassy.core.hardware.BluetoothPrinterClient
import com.azuredevid.cassy.core.hardware.PairedBluetoothDevice
import com.azuredevid.cassy.core.hardware.PrinterEndpoint
import com.azuredevid.cassy.core.hardware.PrinterPort9100Manager
import com.azuredevid.cassy.domain.repository.PrinterConfig
import com.azuredevid.cassy.domain.repository.PrinterSettingsRepository
import com.azuredevid.cassy.domain.repository.StorePrinter
import com.azuredevid.cassy.domain.repository.StorePrinterInput
import com.azuredevid.cassy.domain.repository.StorePrinterRepository
import com.azuredevid.cassy.domain.repository.toPrinterConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
class PrinterSettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private class FakePrinters : StorePrinterRepository {
        val server = mutableListOf(
            StorePrinter("pr-1", "Struk", "receipt", "192.168.1.50:9100", 80),
            StorePrinter("pr-2", "Label", "label", null, 40)
        )
        var calledAll = 0
        var calledTablet = 0
        var created: StorePrinterInput? = null

        override suspend fun listForTablet(): Result<List<StorePrinter>> {
            calledTablet++
            return Result.success(server.filter { it.lanAddress != null })
        }

        override suspend fun listAll(): Result<List<StorePrinter>> {
            calledAll++
            return Result.success(server.toList())
        }

        override suspend fun create(input: StorePrinterInput): Result<StorePrinter> {
            created = input
            val p = StorePrinter("pr-3", input.name, input.station, "${input.lanAddress}:9100", input.paperWidthMm)
            server += p
            return Result.success(p)
        }

        override suspend fun update(id: String, input: StorePrinterInput): Result<StorePrinter> = error("unused")
    }

    private class FakeCache : PrinterSettingsRepository {
        val state = MutableStateFlow(PrinterConfig(cashierPrinterIp = "10.0.0.9"))
        override val configFlow: Flow<PrinterConfig> = state
        override suspend fun getConfig() = state.value
        override suspend fun saveConfig(config: PrinterConfig) { state.value = config }
        override suspend fun clearConfig() { state.value = PrinterConfig() }
    }

    @Test
    fun managerLoadsEveryPrinterAndRefreshesTheTabletCache() = runTest {
        val printers = FakePrinters()
        val cache = FakeCache()
        val vm = PrinterSettingsViewModel(printers, cache, PrinterPort9100Manager())

        vm.load(canManage = true)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, printers.calledAll)
        assertEquals(2, vm.uiState.value.printers.size)
        assertEquals("192.168.1.50", cache.state.value.cashierPrinterIp)
        assertEquals(80, cache.state.value.paperWidthMm)
    }

    @Test
    fun cashierSeesTheTabletListAndCannotEdit() = runTest {
        val printers = FakePrinters()
        val vm = PrinterSettingsViewModel(printers, FakeCache(), PrinterPort9100Manager())

        vm.load(canManage = false)
        dispatcher.scheduler.advanceUntilIdle()
        vm.openNew()

        assertEquals(1, printers.calledTablet)
        assertNull(vm.uiState.value.editor)
    }

    @Test
    fun addingAReceiptPrinterNeedsAnAddressThenReloads() = runTest {
        val printers = FakePrinters()
        val vm = PrinterSettingsViewModel(printers, FakeCache(), PrinterPort9100Manager())
        vm.load(canManage = true)
        dispatcher.scheduler.advanceUntilIdle()

        vm.openNew()
        vm.setName("Struk belakang")
        assertEquals(false, vm.uiState.value.editor?.canSave)
        vm.setLanAddress("192.168.1.51 x")
        vm.save()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("192.168.1.51", printers.created?.lanAddress)
        assertNull(vm.uiState.value.editor)
        assertEquals(2, printers.calledAll)
        assertTrue(vm.uiState.value.printers.any { it.id == "pr-3" })
    }

    @Test
    fun serverListMapsToTheLocalPrintCache() {
        val config = listOf(
            StorePrinter("a", "Bar lama", "bar", "192.168.1.60:9100", isActive = false),
            StorePrinter("b", "Bar", "bar", "192.168.1.61:9101"),
            StorePrinter("c", "Checker", "checker", "192.168.1.62:9100"),
            StorePrinter("d", "Dapur agent", "kitchen", null)
        ).toPrinterConfig()

        assertEquals("192.168.1.61", config.barPrinterIp)
        assertEquals(9101, config.barPrinterPort)
        assertEquals("192.168.1.62", config.expediterPrinterIp)
        assertEquals("", config.kitchenPrinterIp)
        assertEquals("", config.cashierPrinterIp)
        assertEquals(1, config.receiptCopies)
    }

    @Test
    fun receiptPrinterCarriesCopiesAndFooter() {
        val config = listOf(
            StorePrinter("r", "Kasir", "receipt", "192.168.1.70:9100", copies = 9, footer = "Sampai jumpa"),
            StorePrinter("k", "Dapur", "kitchen", "192.168.1.71:9100", copies = 2)
        ).toPrinterConfig()

        assertEquals(5, config.receiptCopies)
        assertEquals("Sampai jumpa", config.receiptFooter)
        assertEquals(2, config.kitchenCopies)
        assertEquals(1, config.barCopies)
    }

    private class FakeBluetoothClient(
        private val devices: List<PairedBluetoothDevice> = listOf(
            PairedBluetoothDevice("RPP02N-58mm", "AA:BB:CC:DD:EE:FF")
        ),
        private val health: PrinterPort9100Manager.PrinterHealth = PrinterPort9100Manager.PrinterHealth.READY
    ) : BluetoothPrinterClient {
        var lastCheckedMac: String? = null
        override fun isBluetoothSupported(): Boolean = true
        override fun isBluetoothEnabled(): Boolean = true
        override fun hasConnectPermission(): Boolean = true
        override fun listBondedDevices(): Result<List<PairedBluetoothDevice>> = Result.success(devices)
        override suspend fun checkHealth(macAddress: String): PrinterPort9100Manager.HealthCheckResult {
            lastCheckedMac = macAddress
            return PrinterPort9100Manager.HealthCheckResult(health, "OK")
        }
        override suspend fun printRawBytes(macAddress: String, payload: ByteArray): Result<Boolean> = Result.success(true)
        override suspend fun openCashDrawer(macAddress: String): Result<Boolean> = Result.success(true)
    }

    @Test
    fun addingBluetoothPrinterSelectsPairedDeviceAndTestsHealth() = runTest {
        val printers = FakePrinters()
        val cache = FakeCache()
        val bt = FakeBluetoothClient()
        val vm = PrinterSettingsViewModel(printers, cache, PrinterPort9100Manager(), bt)

        vm.load(canManage = true)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, vm.uiState.value.pairedBluetoothDevices.size)

        vm.openNew()
        vm.setTransportType(PrinterConfig.TRANSPORT_BLUETOOTH)
        assertEquals(58, vm.uiState.value.editor?.paperWidthMm)
        assertEquals(false, vm.uiState.value.editor?.canSave)

        vm.selectPairedDevice(PairedBluetoothDevice("RPP02N-58mm", "AA:BB:CC:DD:EE:FF"))
        assertEquals(true, vm.uiState.value.editor?.canSave)
        vm.save()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(PrinterConfig.TRANSPORT_BLUETOOTH, printers.created?.transportType)
        assertEquals("AA:BB:CC:DD:EE:FF", printers.created?.bluetoothMac)
        assertEquals("", printers.created?.lanAddress)

        val btPrinter = StorePrinter(
            id = "pr-bt",
            name = "RPP02N-58mm",
            station = "receipt",
            lanAddress = null,
            paperWidthMm = 58,
            transportType = PrinterConfig.TRANSPORT_BLUETOOTH,
            bluetoothMac = "AA:BB:CC:DD:EE:FF"
        )
        vm.test(btPrinter)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("AA:BB:CC:DD:EE:FF", bt.lastCheckedMac)
        assertEquals(true, vm.uiState.value.messageOk)
        assertEquals(PrinterEndpoint.Bluetooth("AA:BB:CC:DD:EE:FF"), listOf(btPrinter).toPrinterConfig().cashierEndpoint())
    }
}
