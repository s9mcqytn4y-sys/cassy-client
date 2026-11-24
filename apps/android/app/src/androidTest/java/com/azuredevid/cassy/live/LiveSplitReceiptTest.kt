package com.azuredevid.cassy.live

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.azuredevid.cassy.domain.model.CashDrawer
import com.azuredevid.cassy.domain.repository.PrinterSettingsRepository
import com.azuredevid.cassy.domain.repository.ShiftRepository
import com.azuredevid.cassy.ui.pos.PosViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.parameter.parametersOf

/**
 * OPS-04 (PrdOpsAndPwaClose.md section 13, S5-Q1): a table bill split through the POS prints one receipt
 * per share. A by_item share lists only its own items; an equal share lists the whole table bill under
 * its own heading. Table U2 is used only here; "Laci Uji 1" is shared with the other sequential live tests.
 */
@RunWith(AndroidJUnit4::class)
class LiveSplitReceiptTest {

    private val koin get() = GlobalContext.get()

    private lateinit var drawer: CashDrawer
    private lateinit var vm: PosViewModel
    private var shiftId: String? = null

    @Before
    fun openShift(): Unit = runBlocking {
        drawer = LiveStack.startOnDrawer("Laci Uji 1")
        shiftId = koin.get<ShiftRepository>().openShift(openingFloat = 100_000L, drawerId = drawer.id).getOrThrow().shift.id
        LiveStack.http("DELETE", LiveStack.PRINTER_JOBS)
        vm = onMain { koin.get<PosViewModel> { parametersOf(SavedStateHandle()) } }
        awaitTrue("catalog") { vm.uiState.value.allProducts.any { it.name == "Nasi Uji" } }
        awaitTrue("server printer config") { koin.get<PrinterSettingsRepository>().getConfig().hasCashierPrinter }
    }

    @After
    fun closeShift() {
        shiftId?.let { id ->
            runCatching {
                val manager = LiveStack.staffToken("manager.e2e@cassy.local", LiveStack.MANAGER_PIN)
                LiveStack.api("POST", "/pos/shifts/$id/force-close", manager, JSONObject().put("reason", "Akhir uji struk bagian"))
            }
        }
    }

    @Test
    fun equalSharesListTheWholeTableBill(): Unit = runBlocking {
        sendKopiTwiceAndNasiToTable()
        onMain { vm.setTabSplitCount(2) }
        onMain { vm.splitTab() }
        awaitTrue("two equal shares") { vm.uiState.value.tabSplits.size == 2 }
        payEveryShare()

        for (n in 1..2) {
            val receipt = awaitJob { "BAGIAN $n DARI 2" in it }
            assertTrue("share $n lists the table bill", "PESANAN MEJA (DIBAGI RATA)" in receipt)
            assertTrue("Kopi Uji" in receipt && "Nasi Uji" in receipt)
            assertFalse("no pointer to a later receipt", "struk akhir" in receipt)
        }
    }

    @Test
    fun byItemSharesListOnlyTheirOwnItems(): Unit = runBlocking {
        sendKopiTwiceAndNasiToTable()
        onMain { vm.openItemSplit() }
        awaitTrue("item split draft") { vm.uiState.value.itemSplit != null }
        onMain { vm.setItemSplitGuests(2) }
        // Guest 1 takes both coffees, guest 2 the rice.
        vm.uiState.value.itemSplit!!.units.forEachIndexed { index, unit ->
            onMain { vm.assignUnit(index, if (unit.name == "Nasi Uji") 2 else 1) }
        }
        onMain { vm.confirmItemSplit() }
        awaitTrue("two item shares") { vm.uiState.value.tabSplits.size == 2 }
        payEveryShare()

        val kopiShare = awaitJob { "BAGIAN" in it && "Kopi Uji" in it }
        val nasiShare = awaitJob { "BAGIAN" in it && "Nasi Uji" in it }
        assertFalse("the coffee share does not list the rice", "Nasi Uji" in kopiShare)
        assertFalse("the rice share does not list the coffee", "Kopi Uji" in nasiShare)
        assertFalse("PESANAN MEJA (DIBAGI RATA)" in kopiShare)
    }

    private suspend fun sendKopiTwiceAndNasiToTable() {
        onMain { vm.loadTables() }
        awaitTrue("tables") { vm.uiState.value.tables.any { it.tableNumber == "U2" } }
        onMain { vm.selectTable(vm.uiState.value.tables.first { it.tableNumber == "U2" }) }
        val products = vm.uiState.value.allProducts.associateBy { it.name }
        onMain { vm.addProductToCart(products.getValue("Kopi Uji")) }
        onMain { vm.addProductToCart(products.getValue("Kopi Uji")) }
        onMain { vm.addProductToCart(products.getValue("Nasi Uji")) }
        onMain { vm.sendToKitchen() }
        awaitTrue("round sent") { vm.uiState.value.cart.isNotEmpty() && vm.uiState.value.cart.all { it.sentLines.isNotEmpty() } }
    }

    private suspend fun payEveryShare() {
        repeat(2) {
            val share = vm.uiState.value.selectedSplit ?: throw AssertionError("no share left to pay")
            onMain { vm.submitCashPayment(tenderedAmount = share.totalAmount + 1_000L, drawerId = drawer.id, shiftId = shiftId) }
            awaitTrue("share ${share.number} paid") {
                vm.uiState.value.tabSplits.firstOrNull { it.id == share.id }?.isPaid != false
            }
        }
        awaitTrue("bill settled") { vm.uiState.value.tabSplits.isEmpty() || vm.uiState.value.tabSplits.all { it.isPaid } }
        assertEquals(0, vm.uiState.value.tabSplits.count { !it.isPaid })
    }

    private suspend fun awaitJob(match: (String) -> Boolean): String {
        var found = ""
        awaitTrue("printer job") {
            val jobs = JSONArray(LiveStack.http("GET", LiveStack.PRINTER_JOBS))
            found = (0 until jobs.length()).map { jobs.getJSONObject(it).getString("text") }.firstOrNull(match).orEmpty()
            found.isNotEmpty()
        }
        return found
    }

    private suspend fun awaitTrue(what: String, condition: suspend () -> Boolean) {
        try {
            withTimeout(20_000L) { while (!condition()) delay(300) }
        } catch (e: Exception) {
            throw AssertionError("timed out waiting for $what", e)
        }
    }

    private fun <T> onMain(block: () -> T): T {
        var result: T? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync { result = block() }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }
}
