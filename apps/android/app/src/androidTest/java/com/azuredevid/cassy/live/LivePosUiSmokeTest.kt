package com.azuredevid.cassy.live

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.azuredevid.cassy.R
import com.azuredevid.cassy.domain.repository.ShiftRepository
import com.azuredevid.cassy.ui.pos.PosLandscapeScreen
import com.azuredevid.cassy.ui.theme.CassyTheme
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

/**
 * S2-Q1 UI smoke: the real cashier screen with its Koin view models against the Docker test stack.
 * One tap path per tender (cash, EDC) proves the buttons and modals are wired to the server and the
 * printer; the money rules themselves are covered by [LiveCashierLifecycleTest].
 */
@RunWith(AndroidJUnit4::class)
class LivePosUiSmokeTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val koin get() = GlobalContext.get()
    private val context get() = compose.activity
    private var shiftId: String? = null

    @Before
    fun loginAndOpenShift(): Unit = runBlocking {
        val drawer = LiveStack.startOnDrawer("Laci Uji 1")
        shiftId = koin.get<ShiftRepository>().openShift(openingFloat = 0L, drawerId = drawer.id).getOrThrow().shift.id
        LiveStack.http("DELETE", LiveStack.PRINTER_JOBS)
    }

    @After
    fun closeShift(): Unit = runBlocking {
        val id = shiftId ?: return@runBlocking
        // Blind count is not under test here: a manager force-closes the shift (K-04 shift.force_close).
        LiveStack.loginManager()
        koin.get<ShiftRepository>().forceCloseShift(id, reason = "Pembersihan uji e2e").getOrThrow()
    }

    @Test
    fun cashAndEdcTenderFromTheCashierScreen() {
        compose.setContent { CassyTheme(darkTheme = false) { PosLandscapeScreen(onNavigateToSettings = {}) } }

        sellOne()
        compose.onNodeWithText(context.getString(R.string.btn_pay_cash)).performClick()
        compose.onNodeWithText(context.getString(R.string.btn_complete_and_print)).performClick()
        awaitPrinted("TUNAI")

        sellOne()
        compose.onNodeWithText(context.getString(R.string.btn_pay_edc)).performClick()
        compose.onNodeWithText(context.getString(R.string.input_edc_reference_label)).performTextInput("APPR-UI-01")
        compose.onNodeWithText(context.getString(R.string.btn_complete_and_print)).performClick()
        awaitPrinted("EDC")
    }

    private fun sellOne() {
        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodesWithText("Kopi Uji").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Kopi Uji").performClick()
        compose.waitUntil(TIMEOUT_MS) {
            runCatching { compose.onNodeWithText(context.getString(R.string.btn_pay_cash)).assertIsEnabled() }.isSuccess
        }
    }

    private fun awaitPrinted(methodLabel: String) {
        compose.waitUntil(TIMEOUT_MS) {
            val jobs = JSONArray(LiveStack.http("GET", LiveStack.PRINTER_JOBS))
            (0 until jobs.length()).any { i ->
                val text = jobs.getJSONObject(i).getString("text")
                "Terima kasih - E2E" in text && methodLabel in text
            }
        }
    }


    private companion object {
        const val TIMEOUT_MS = 15_000L
    }
}
