package com.azuredevid.cassy.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream
import com.azuredevid.cassy.R
import com.azuredevid.cassy.core.security.SessionManager
import com.azuredevid.cassy.data.model.CatalogCategory
import com.azuredevid.cassy.data.model.CatalogProduct
import com.azuredevid.cassy.data.model.CatalogSnapshot
import com.azuredevid.cassy.data.model.CatalogVariant
import com.azuredevid.cassy.data.model.OrderQuote
import com.azuredevid.cassy.data.model.OrderQuoteRequest
import com.azuredevid.cassy.data.model.ProductUpsertInput
import com.azuredevid.cassy.data.model.VariantUpsertInput
import com.azuredevid.cassy.data.repository.ServerSettingsRepository
import com.azuredevid.cassy.domain.model.CashDrawer
import com.azuredevid.cassy.domain.model.CashMovement
import com.azuredevid.cassy.domain.model.CashMovementResult
import com.azuredevid.cassy.domain.model.CashMovementType
import com.azuredevid.cassy.domain.model.CreatePosOrderInput
import com.azuredevid.cassy.domain.model.InitiatePosPaymentInput
import com.azuredevid.cassy.domain.model.InitiatePosPaymentResult
import com.azuredevid.cassy.domain.model.MidtransEnvironment
import com.azuredevid.cassy.domain.model.OfflinePinVerifier
import com.azuredevid.cassy.domain.model.PaymentCredentialInput
import com.azuredevid.cassy.domain.model.PaymentCredentials
import com.azuredevid.cassy.domain.model.PosOrder
import com.azuredevid.cassy.domain.model.PosOrderDetail
import com.azuredevid.cassy.domain.model.PosPaymentAttempt
import com.azuredevid.cassy.domain.model.QRServiceMode
import com.azuredevid.cassy.domain.model.Shift
import com.azuredevid.cassy.domain.model.ShiftCashSummary
import com.azuredevid.cassy.domain.model.ShiftCloseResult
import com.azuredevid.cassy.domain.model.ShiftOpenResult
import com.azuredevid.cassy.domain.model.ShiftStatus
import com.azuredevid.cassy.domain.model.StaffIdentity
import com.azuredevid.cassy.domain.model.StoreCMSConfig
import com.azuredevid.cassy.domain.model.StoreCMSConfigInput
import com.azuredevid.cassy.domain.model.StoreSettings
import com.azuredevid.cassy.domain.model.StoreSettingsPatch
import com.azuredevid.cassy.domain.model.StoredCookie
import com.azuredevid.cassy.domain.model.StoredSession
import com.azuredevid.cassy.domain.model.XReport
import com.azuredevid.cassy.domain.model.ZReport
import com.azuredevid.cassy.domain.repository.AuthLoginResult
import com.azuredevid.cassy.domain.repository.AuthRepository
import com.azuredevid.cassy.domain.repository.CatalogRepository
import com.azuredevid.cassy.domain.repository.DiscoveredLocalServer
import com.azuredevid.cassy.domain.repository.LocalServerDiscoveryRepository
import com.azuredevid.cassy.domain.repository.MobileBootstrapData
import com.azuredevid.cassy.domain.repository.OrderQuoteRepository
import com.azuredevid.cassy.domain.repository.PosTable
import com.azuredevid.cassy.domain.repository.ServiceRequest
import com.azuredevid.cassy.ui.pos.components.PosServiceRequestPill
import com.azuredevid.cassy.ui.pos.components.PosServiceRequestsModal
import com.azuredevid.cassy.ui.pos.components.PosTabItemVoidDialog
import com.azuredevid.cassy.ui.pos.components.PosTabSplitSection
import com.azuredevid.cassy.ui.pos.components.PosItemSplitDialog
import com.azuredevid.cassy.ui.pos.ItemSplitDraft
import com.azuredevid.cassy.ui.pos.SplitUnit
import com.azuredevid.cassy.domain.repository.TabSplit
import com.azuredevid.cassy.ui.pos.TabItemVoidUiState
import com.azuredevid.cassy.ui.pos.TabVoidStep
import com.azuredevid.cassy.ui.pos.CartItem
import com.azuredevid.cassy.ui.pos.components.PosOrderSummaryPanel
import com.azuredevid.cassy.domain.repository.PosOrderRepository
import com.azuredevid.cassy.domain.repository.PrinterConfig
import com.azuredevid.cassy.core.hardware.PrinterPort9100Manager
import com.azuredevid.cassy.domain.repository.PrinterSettingsRepository
import com.azuredevid.cassy.domain.repository.StorePrinter
import com.azuredevid.cassy.domain.repository.StorePrinterInput
import com.azuredevid.cassy.domain.repository.StorePrinterRepository
import com.azuredevid.cassy.ui.settings.PrinterSettingsViewModel
import com.azuredevid.cassy.ui.settings.DisplayScreensViewModel
import com.azuredevid.cassy.domain.repository.CreatedDisplayScreen
import com.azuredevid.cassy.domain.repository.DisplayScreen
import com.azuredevid.cassy.domain.repository.DisplayTokenRepository
import com.azuredevid.cassy.domain.repository.ServerConnectionStatus
import com.azuredevid.cassy.domain.repository.ServerConnectivityState
import com.azuredevid.cassy.domain.repository.ServerHealthMonitor
import com.azuredevid.cassy.domain.repository.ShiftRepository
import com.azuredevid.cassy.domain.repository.StoreGovernanceRepository
import com.azuredevid.cassy.ui.catalog.CatalogManagementScreen
import com.azuredevid.cassy.ui.catalog.CatalogManagementViewModel
import com.azuredevid.cassy.ui.catalog.components.CategoryFormDialog
import com.azuredevid.cassy.ui.catalog.components.ProductFormDialog
import com.azuredevid.cassy.ui.components.CassyStartupSplash
import com.azuredevid.cassy.ui.pairing.StorePairingScreen
import com.azuredevid.cassy.ui.pairing.StorePairingUiEvent
import com.azuredevid.cassy.ui.pairing.StorePairingViewModel
import com.azuredevid.cassy.ui.pos.PosLandscapeScreen
import com.azuredevid.cassy.ui.pos.PosViewModel
import com.azuredevid.cassy.ui.pos.components.PosCashModal
import com.azuredevid.cassy.ui.pos.components.PosQrisModal
import com.azuredevid.cassy.ui.settings.ServerConfigScreen
import com.azuredevid.cassy.ui.settings.ServerConfigViewModel
import com.azuredevid.cassy.ui.settings.StoreSettingsScreen
import com.azuredevid.cassy.ui.settings.StoreSettingsViewModel
import com.azuredevid.cassy.ui.shift.ShiftViewModel
import com.azuredevid.cassy.ui.shift.components.CashMovementModal
import com.azuredevid.cassy.ui.shift.components.CloseShiftModal
import com.azuredevid.cassy.ui.shift.components.OpenShiftModal
import com.azuredevid.cassy.ui.shift.components.XReportModal
import com.azuredevid.cassy.data.network.RealtimeConnectionState
import com.azuredevid.cassy.data.network.RealtimeEventEnvelope
import com.azuredevid.cassy.domain.model.KdsStation
import com.azuredevid.cassy.domain.model.KdsTicket
import com.azuredevid.cassy.domain.model.KdsTicketLine
import com.azuredevid.cassy.domain.model.KdsTicketStatus
import com.azuredevid.cassy.domain.repository.KdsRepository
import com.azuredevid.cassy.domain.repository.RealtimeEventSource
import com.azuredevid.cassy.ui.kds.KdsScreen
import com.azuredevid.cassy.ui.kds.KdsViewModel
import com.azuredevid.cassy.ui.theme.CassyTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import android.view.accessibility.AccessibilityNodeInfo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.text.NumberFormat
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class CassyUiShowcaseTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    /**
     * The CI emulator often shows "Pixel Launcher isn't responding" over the app. When the active
     * window is that system dialog, press Wait so the full-display capture shows Cassy, not the ANR.
     */
    private fun dismissSystemAnrDialog() {
        val root = InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow ?: return
        if (root.packageName != "android") return
        root.findAccessibilityNodeInfosByText("Wait").firstOrNull()?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        SystemClock.sleep(500L)
    }

    private fun captureScreen(name: String, preferFullDisplay: Boolean = false) {
        composeRule.waitForIdle()
        if (preferFullDisplay) dismissSystemAnrDialog()
        runCatching {
            val fullDisplayBitmap = if (preferFullDisplay) {
                SystemClock.sleep(500L)
                InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            } else {
                null
            }
            val bitmap = fullDisplayBitmap ?: run {
                val decorView = composeRule.activity.window.decorView
                val width = decorView.width.coerceAtLeast(1)
                val height = decorView.height.coerceAtLeast(1)
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bmp ->
                    val canvas = Canvas(bmp)
                    composeRule.runOnUiThread {
                        decorView.draw(canvas)
                    }
                }
            }
            val dir = File(context.filesDir, "showcase")
            val outFile = File(dir, "$name.png").apply {
                parentFile?.mkdirs()
            }
            FileOutputStream(outFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            Log.i("CassyShowcase", "Saved screenshot: ${outFile.absolutePath} (${outFile.length()} bytes)")
        }.onFailure { err ->
            Log.w("CassyShowcase", "Failed screenshot $name", err)
        }
    }

    private fun ownerSession() = StoredSession(
        accessToken = "showcase-token",
        accessTokenExpiresAt = "2027-01-01T00:00:00Z",
        sessionExpiresAt = "2027-01-02T00:00:00Z",
        staff = StaffIdentity(
            userId = "staff-1",
            fullName = "Raka Pratama",
            email = "owner@cassy.id",
            role = "owner",
            storeId = "store-1",
            storeSlug = "warung-pandan",
            storeName = "Warung Pandan Nusantara"
        ),
        permissions = listOf("store.settings.manage", "catalog.manage", "pos.operate")
    )

    private class ShowcaseSessionManager(initial: StoredSession?) : SessionManager {
        private val flow = MutableStateFlow(initial)
        override val sessionFlow: StateFlow<StoredSession?> = flow
        override fun currentSession(): StoredSession? = flow.value
        override fun getAccessToken(): String? = flow.value?.accessToken
        override fun getStoreSlug(): String? = flow.value?.staff?.storeSlug
        override fun getDeviceCredential(): String? = flow.value?.deviceCredential
        override suspend fun awaitLoaded(): StoredSession? = flow.value
        override suspend fun saveLoginSession(
            accessToken: String,
            accessTokenExpiresAt: String,
            sessionExpiresAt: String,
            staff: StaffIdentity,
            permissions: List<String>,
            pinChangeRequired: Boolean,
            deviceCredential: String?,
            offlinePinVerifier: OfflinePinVerifier?
        ) {
            flow.value = StoredSession(
                accessToken = accessToken,
                accessTokenExpiresAt = accessTokenExpiresAt,
                sessionExpiresAt = sessionExpiresAt,
                staff = staff,
                permissions = permissions
            )
        }

        override suspend fun updateRefreshedTokens(
            accessToken: String,
            accessTokenExpiresAt: String,
            sessionExpiresAt: String,
            staff: StaffIdentity?,
            permissions: List<String>?
        ) = Unit

        override suspend fun saveCookies(cookies: List<StoredCookie>) = Unit
        override suspend fun getCookies(): List<StoredCookie> = emptyList()
        override suspend fun clearSession(preserveDeviceBinding: Boolean) {
            flow.value = null
        }
    }

    private class ShowcaseServerSettingsRepo(
        private var url: String = "https://api.abbdullaziz.online"
    ) : ServerSettingsRepository {
        override fun getBaseUrl(): String = url
        override fun setBaseUrl(url: String) {
            this.url = url
        }
    }

    private class ShowcasePrinterSettingsRepo : PrinterSettingsRepository {
        private val state = MutableStateFlow(PrinterConfig(cashierPrinterIp = "192.168.10.200"))
        override val configFlow: Flow<PrinterConfig> = state
        override suspend fun getConfig(): PrinterConfig = state.value
        override suspend fun saveConfig(config: PrinterConfig) {
            state.value = config
        }
        override suspend fun clearConfig() {
            state.value = PrinterConfig()
        }
    }

    private class ShowcaseStorePrinters : StorePrinterRepository {
        private val printers = listOf(
            StorePrinter("pr-1", "Struk kasir depan", "receipt", "192.168.10.200:9100", 80),
            StorePrinter("pr-2", "Dapur panas", "kitchen", "192.168.10.201:9100", 58),
            StorePrinter("pr-3", "Bar kopi", "bar", "192.168.10.202:9100", 58),
            StorePrinter("pr-4", "Label cup", "label", null, 40)
        )
        override suspend fun listForTablet() = Result.success(printers.filter { it.lanAddress != null })
        override suspend fun listAll() = Result.success(printers)
        override suspend fun create(input: StorePrinterInput) = Result.success(printers.first())
        override suspend fun update(id: String, input: StorePrinterInput) = Result.success(printers.first())
    }

    private class ShowcaseDisplayTokens : DisplayTokenRepository {
        private val screens = listOf(
            DisplayScreen("d-1", "TV kasir depan", "2026-11-19T08:00:00Z", lastSeenAt = "2026-11-19T12:41:00Z"),
            DisplayScreen("d-2", "TV lama", "2026-11-10T08:00:00Z", revokedAt = "2026-11-18T09:00:00Z")
        )
        override suspend fun list() = Result.success(screens)
        override suspend fun create(name: String) = Result.success(
            CreatedDisplayScreen(
                DisplayScreen("d-3", name, "2026-11-19T13:00:00Z"),
                "s1.d-3.secret",
                "https://order.cassy.id/antrean#t=s1.d-3.secret"
            )
        )
        override suspend fun revoke(id: String) = Result.success(Unit)
    }

    private fun showcasePrinterViewModel() =
        PrinterSettingsViewModel(ShowcaseStorePrinters(), ShowcasePrinterSettingsRepo(), PrinterPort9100Manager())

    private class ShowcaseAuthRepository : AuthRepository {
        override suspend fun login(
            storeSlug: String,
            email: String,
            badge: String,
            pin: String,
            deviceCredential: String?
        ): Result<AuthLoginResult> = Result.failure(UnsupportedOperationException())
        override suspend fun fetchMobileBootstrap(): Result<MobileBootstrapData> =
            Result.failure(UnsupportedOperationException())
        override suspend fun fetchMe(): Result<StaffIdentity> = Result.failure(UnsupportedOperationException())
        override suspend fun logout(): Result<Unit> = Result.success(Unit)
    }

    private val sampleCategories = listOf(
        CatalogCategory("cat-food", "Makanan Utama", 1, true),
        CatalogCategory("cat-drink", "Minuman & Teh", 2, true),
        CatalogCategory("cat-pastry", "Roti & Kue", 3, true)
    )

    private val sampleProducts = listOf(
        CatalogProduct(
            id = "p-1",
            name = "Paket Nasi Ayam Bakar",
            categoryId = "cat-food",
            categoryName = "Makanan Utama",
            description = "Ayam bakar bumbu kecap + sambal terasi",
            basePriceIdr = 32000L,
            imageUrl = "",
            prepStation = "kitchen",
            isAvailable = true,
            stockQuantity = 24,
            variants = listOf(CatalogVariant("v-1", "Reguler", price = 32000L, isDefault = true))
        ),
        CatalogProduct(
            id = "p-2",
            name = "Es Pandan Latte",
            categoryId = "cat-drink",
            categoryName = "Minuman & Teh",
            description = "Espresso house blend dengan sirup pandan",
            basePriceIdr = 26000L,
            imageUrl = "",
            prepStation = "bar",
            isAvailable = true,
            stockQuantity = 18
        ),
        CatalogProduct(
            id = "p-3",
            name = "Croissant Mentega",
            categoryId = "cat-pastry",
            categoryName = "Roti & Kue",
            description = "Lapis renyah panggang pagi",
            basePriceIdr = 22000L,
            imageUrl = "",
            prepStation = "pastry",
            isAvailable = false,
            stockQuantity = 0
        )
    )

    private class ShowcaseCatalogRepo(
        private val categories: List<CatalogCategory>,
        private val products: List<CatalogProduct>
    ) : CatalogRepository {
        override suspend fun getCatalog(storeSlug: String): Result<List<CatalogProduct>> = Result.success(products)
        override suspend fun getStaffCatalog(): Result<CatalogSnapshot> =
            Result.success(CatalogSnapshot(categories, products))
        override suspend fun createCategory(name: String, displayOrder: Int, isActive: Boolean) =
            Result.failure<CatalogCategory>(UnsupportedOperationException())
        override suspend fun updateCategory(id: String, name: String, displayOrder: Int, isActive: Boolean) =
            Result.failure<CatalogCategory>(UnsupportedOperationException())
        override suspend fun deleteCategory(id: String) = Result.success(Unit)
        override suspend fun createProduct(input: ProductUpsertInput) =
            Result.failure<CatalogProduct>(UnsupportedOperationException())
        override suspend fun updateProduct(id: String, input: ProductUpsertInput) =
            Result.failure<CatalogProduct>(UnsupportedOperationException())
        override suspend fun archiveProduct(id: String) = Result.success(Unit)
        override suspend fun setProductAvailability(id: String, isAvailable: Boolean) = Result.success(Unit)
        override suspend fun createVariant(productId: String, input: VariantUpsertInput) =
            Result.failure<CatalogVariant>(UnsupportedOperationException())
        override suspend fun updateVariant(variantId: String, input: VariantUpsertInput) =
            Result.failure<CatalogVariant>(UnsupportedOperationException())
        override suspend fun archiveVariant(variantId: String) = Result.success(Unit)
        override suspend fun uploadProductImage(productId: String, webpBytes: ByteArray) =
            Result.failure<String>(UnsupportedOperationException())
    }

    private class ShowcaseShiftRepo : ShiftRepository {
        private val drawer = CashDrawer("drw-1", "store-1", "Laci Utama", true)
        private val openShift = Shift(
            id = "shift-ab1234",
            storeId = "store-1",
            drawerId = "drw-1",
            openedBy = "staff-1",
            openedByName = "Raka Pratama",
            openingFloat = 200_000L,
            openedAt = "2026-11-14T08:00:00Z",
            status = ShiftStatus.OPEN,
            expectedCash = 264_000L
        )
        override suspend fun getDrawers(): Result<List<CashDrawer>> = Result.success(listOf(drawer))
        override suspend fun getCurrentShift(drawerId: String?): Result<Shift?> = Result.success(openShift)
        override suspend fun openShift(openingFloat: Long, drawerId: String?, notes: String): Result<ShiftOpenResult> =
            Result.success(ShiftOpenResult(openShift, openDrawer = true))
        override suspend fun recordCashMovement(
            movementType: CashMovementType,
            amount: Long,
            reason: String,
            shiftId: String?,
            drawerId: String?
        ): Result<CashMovementResult> = Result.success(
            CashMovementResult(
                CashMovement("mov-1", "store-1", "shift-ab1234", movementType, amount, "staff-1", reason, "2026-11-14T09:00:00Z"),
                openDrawer = false
            )
        )
        override suspend fun getXReport(shiftId: String): Result<XReport> = Result.success(
            XReport(
                shiftId = shiftId,
                storeId = "store-1",
                drawerId = "drw-1",
                drawerName = "Laci Utama",
                openedBy = "staff-1",
                openedByName = "Raka Pratama",
                openedAt = "2026-11-14T08:00:00Z",
                generatedAt = "2026-11-14T12:00:00Z",
                summary = ShiftCashSummary(openingFloat = 200_000L, cashSales = 64_000L),
                expectedCash = 264_000L
            )
        )
        override suspend fun closeShift(
            shiftId: String,
            countedCash: Long,
            ownerPin: String?,
            varianceReason: String?,
            notes: String?
        ): Result<ShiftCloseResult> = Result.failure(UnsupportedOperationException())
        override suspend fun getZReport(shiftId: String): Result<ZReport> =
            Result.failure(UnsupportedOperationException())
    }

    private class ShowcaseGovernanceRepo : StoreGovernanceRepository {
        override suspend fun getPaymentCredentials(): Result<PaymentCredentials> = Result.success(
            PaymentCredentials(
                environment = MidtransEnvironment.SANDBOX,
                merchantId = "G357841497",
                clientKey = "SB-Mid-client-showcase1234",
                serverKeyMasked = "SB-Mid-server-••••1234",
                isEnabled = true,
                fallbackStaticQrisEnabled = true,
                validationStatus = "VERIFIED",
                lastVerifiedAt = "2026-11-14T10:00:00Z"
            )
        )
        override suspend fun savePaymentCredentials(input: PaymentCredentialInput): Result<PaymentCredentials> =
            getPaymentCredentials()
        override suspend fun getStoreCMS(): Result<StoreCMSConfig> = Result.success(
            StoreCMSConfig(
                defaultLocale = "id-ID",
                supportedLocales = listOf("id-ID", "en-US"),
                heroTitleId = "Selamat Datang di Warung Pandan",
                heroTitleEn = "Welcome to Warung Pandan",
                heroSubtitleId = "Pesan langsung dari meja",
                heroSubtitleEn = "Order directly from your table",
                heroBannerUrl = "",
                primaryHex = "#1F5F52",
                accentHex = "#E9A23B",
                highContrastMode = false,
                hidePoweredByBadge = false,
                customDomain = ""
            )
        )
        override suspend fun saveStoreCMS(input: StoreCMSConfigInput): Result<StoreCMSConfig> = getStoreCMS()
        override suspend fun getStoreSettings(): Result<StoreSettings> = Result.success(
            StoreSettings(
                qrServiceMode = QRServiceMode.TABLE,
                qrOrderingPaused = false,
                tableTimeLimitMinutes = 90,
                tableTimerWarningMinutes = 15,
                staticQrisFallbackMode = "cashier",
                cashierHandlesBar = false
            )
        )
        override suspend fun updateStoreSettings(patch: StoreSettingsPatch): Result<StoreSettings> = getStoreSettings()
    }

    @Test
    fun serverConfigScreen_rendersSplitPaneAndPresets() {
        val fakeDiscovery = object : LocalServerDiscoveryRepository {
            override suspend fun discoverLocalServers(timeoutMs: Long): List<DiscoveredLocalServer> {
                return listOf(
                    DiscoveredLocalServer(
                        serviceName = "Cassy Roastery Senopati (LAN)",
                        host = "192.168.1.100",
                        port = 8080,
                        baseUrl = "http://192.168.1.100:8080",
                        isTls = false,
                        latencyMs = 12L
                    )
                )
            }
        }
        val vm = ServerConfigViewModel(
            serverSettings = ShowcaseServerSettingsRepo(),
            localDiscovery = fakeDiscovery,
            enforceHttpsOnly = false
        )
        composeRule.setContent {
            CassyTheme(darkTheme = false) {
                ServerConfigScreen(viewModel = vm, onNavigateBack = {})
            }
        }
        composeRule.onNodeWithText(context.getString(R.string.server_config_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.server_config_test_btn)).assertIsDisplayed()
        composeRule.onAllNodesWithText(context.getString(R.string.server_config_preset_lan)).filterToOne(hasClickAction()).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.server_config_discover_mdns)).assertIsDisplayed()
        composeRule.onAllNodesWithText(context.getString(R.string.server_config_preset_demo)).filterToOne(hasClickAction()).performClick()
        composeRule.onNodeWithContentDescription(context.getString(R.string.server_config_clear_url_cd)).assertIsDisplayed()
        captureScreen("ServerConfig/DefaultCloudHttps")

        composeRule.onAllNodesWithText(context.getString(R.string.server_config_preset_lan)).filterToOne(hasClickAction()).performClick()
        composeRule.waitForIdle()
        captureScreen("ServerConfig/Tabs/LocalStoreLanTab")

        composeRule.onAllNodesWithText(context.getString(R.string.server_config_preset_emulator)).filterToOne(hasClickAction()).performClick()
        composeRule.waitForIdle()
        captureScreen("ServerConfig/Tabs/LocalEmulatorTab")

        composeRule.runOnIdle {
            vm.onUrlChanged("ftp://invalid-endpoint")
            vm.save()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.server_config_err_url)).assertIsDisplayed()
        captureScreen("ServerConfig/Errors/InvalidUrlValidation")

        composeRule.runOnIdle {
            vm.discoverLocalServers()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.server_config_mdns_dialog_title)).assertIsDisplayed()
        captureScreen("ServerConfig/Modals/MdnsDiscoveryDialog", preferFullDisplay = true)
        composeRule.runOnIdle {
            vm.dismissMdnsDialog()
        }
    }

    private class ShowcaseHealthMonitor(
        initialStatus: ServerConnectionStatus = ServerConnectionStatus.ONLINE,
        latencyMs: Long? = 24L
    ) : ServerHealthMonitor {
        private val flow = MutableStateFlow(ServerConnectivityState(initialStatus, latencyMs))
        override fun observeConnectivity(): Flow<ServerConnectivityState> = flow
        override fun requestProbe() = Unit
        override suspend fun probeNow(): ServerConnectivityState = flow.value
    }

    @Test
    fun startupSplash_rendersBrandMonogramAndProgress() {
        composeRule.setContent {
            CassyTheme(darkTheme = false) {
                CassyStartupSplash()
            }
        }
        composeRule.onNodeWithText(context.getString(R.string.app_name)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.splash_loading_subtitle)).assertIsDisplayed()
        captureScreen("StartupSplash/ComposeSplash")
    }

    @Test
    fun storePairingScreen_rendersBrandingShowcaseAndAuthForm() {
        val vm = StorePairingViewModel(
            authRepository = ShowcaseAuthRepository(),
            healthMonitor = ShowcaseHealthMonitor(ServerConnectionStatus.ONLINE, 24L)
        )
        composeRule.setContent {
            CassyTheme(darkTheme = false) {
                StorePairingScreen(viewModel = vm, onPairingSuccess = { _, _ -> })
            }
        }
        composeRule.onNodeWithText(context.getString(R.string.pairing_screen_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.btn_scan_qr_camera)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.auth_btn_sign_in)).performScrollTo().assertIsDisplayed()
        captureScreen("StorePairing/DefaultOnline")
    }

    @Test
    fun storePairingScreen_rendersValidationErrorsOfflineStatusAndHidDialog() {
        val vm = StorePairingViewModel(
            authRepository = ShowcaseAuthRepository(),
            healthMonitor = ShowcaseHealthMonitor(ServerConnectionStatus.SERVER_UNREACHABLE, null)
        )
        composeRule.setContent {
            CassyTheme(darkTheme = false) {
                StorePairingScreen(viewModel = vm, onPairingSuccess = { _, _ -> })
            }
        }
        composeRule.runOnIdle {
            vm.onEvent(StorePairingUiEvent.SubmitLogin)
        }
        composeRule.waitForIdle()
        // The chip has no scroll parent on tablet, and on a phone the validation error can push it off screen.
        composeRule.onNodeWithText(context.getString(R.string.pairing_server_status_server_unreachable))
            .assertExists()
        composeRule.onNodeWithText(context.getString(R.string.auth_err_store_slug_required)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.auth_btn_sign_in)).performScrollTo().assertIsDisplayed()
        captureScreen("StorePairing/Errors/ValidationAndOffline")

        composeRule.runOnIdle {
            vm.onEvent(StorePairingUiEvent.CameraPermissionDenied)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.pairing_camera_permission_denied_title))
            .assertIsDisplayed()
        captureScreen("StorePairing/Modals/CameraPermissionModal", preferFullDisplay = true)

        composeRule.runOnIdle {
            vm.onEvent(StorePairingUiEvent.OpenHidScanner(true))
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.pairing_hid_dialog_title)).assertIsDisplayed()
        runCatching {
            composeRule.waitUntil(timeoutMillis = 1_500) {
                composeRule.onAllNodesWithText(context.getString(R.string.pairing_hid_ready_scan))
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
        }
        if (
            composeRule.onAllNodesWithText(context.getString(R.string.pairing_hid_ready_scan))
                .fetchSemanticsNodes()
                .isEmpty()
        ) {
            composeRule.onNodeWithText(context.getString(R.string.pairing_hid_field_label)).performClick()
            composeRule.waitForIdle()
        }
        composeRule.onNodeWithText(context.getString(R.string.pairing_hid_ready_scan)).assertIsDisplayed()
        captureScreen("StorePairing/Modals/HidScannerDialog", preferFullDisplay = true)
    }

    @Test
    fun storePairingScreen_rendersCameraScannerOverlay() {
        val vm = StorePairingViewModel(
            authRepository = ShowcaseAuthRepository(),
            healthMonitor = ShowcaseHealthMonitor(ServerConnectionStatus.ONLINE, 19L)
        )
        composeRule.setContent {
            CassyTheme(darkTheme = false) {
                StorePairingScreen(viewModel = vm, onPairingSuccess = { _, _ -> })
            }
        }
        composeRule.runOnIdle {
            vm.onEvent(StorePairingUiEvent.ToggleCamera(true))
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.scanner_reticle_hint)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.pairing_btn_hid_scanner)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.scanner_close_cd)).assertIsDisplayed()
        captureScreen("StorePairing/Modals/QrCameraScanner", preferFullDisplay = true)
    }

    @Test
    fun posLandscapeScreen_rendersCatalogShiftAndCartInteraction() {
        val sessionManager = ShowcaseSessionManager(ownerSession())
        val quoteRepo = object : OrderQuoteRepository {
            override suspend fun quote(request: OrderQuoteRequest): Result<OrderQuote> = Result.success(
                OrderQuote(subtotalAmount = 32_000L, taxAmount = 3_200L, totalAmount = 35_200L)
            )
        }
        val orderRepo = object : PosOrderRepository {
            override suspend fun createOrder(input: CreatePosOrderInput): Result<PosOrder> =
                Result.failure(UnsupportedOperationException())
            override suspend fun initiatePayment(input: InitiatePosPaymentInput): Result<InitiatePosPaymentResult> =
                Result.failure(UnsupportedOperationException())
            override suspend fun getOrder(orderId: String): Result<PosOrderDetail> =
                Result.failure(UnsupportedOperationException())
        }
        val posVm = PosViewModel(
            catalogRepository = ShowcaseCatalogRepo(sampleCategories, sampleProducts),
            orderQuoteRepository = quoteRepo,
            posOrderRepository = orderRepo,
            printerSettings = ShowcasePrinterSettingsRepo(),
            sessionManager = sessionManager
        )
        val shiftVm = ShiftViewModel(
            shiftRepository = ShowcaseShiftRepo(),
            printerSettings = ShowcasePrinterSettingsRepo(),
            sessionManager = sessionManager
        )
        composeRule.setContent {
            CassyTheme(darkTheme = false) {
                PosLandscapeScreen(
                    posViewModel = posVm,
                    shiftViewModel = shiftVm,
                    onNavigateToSettings = {}
                )
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Paket Nasi Ayam Bakar").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Paket Nasi Ayam Bakar").performClick()
        composeRule.waitForIdle()
        val hasTabletPayCash = composeRule.onAllNodesWithText(context.getString(R.string.btn_pay_cash))
            .fetchSemanticsNodes().isNotEmpty()
        if (hasTabletPayCash) {
            composeRule.onNodeWithText(context.getString(R.string.btn_pay_cash)).assertIsDisplayed()
            captureScreen("PosLandscape/PosMain", preferFullDisplay = true)
        } else {
            composeRule.onNodeWithText(context.getString(R.string.pos_mobile_view_cart)).assertIsDisplayed()
            captureScreen("PosLandscape/PosMain", preferFullDisplay = true)
            composeRule.onNodeWithText(context.getString(R.string.pos_mobile_view_cart)).performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithText(context.getString(R.string.btn_pay_cash)).assertIsDisplayed()
            captureScreen("PosLandscape/Modals/PosMobileCartSheet", preferFullDisplay = true)
        }
    }

    @Test
    fun catalogManagementScreen_andDialogsRenderCleanly() {
        val vm = CatalogManagementViewModel(
            catalogRepository = ShowcaseCatalogRepo(sampleCategories, sampleProducts),
            sessionManager = ShowcaseSessionManager(ownerSession())
        )
        composeRule.setContent {
            CassyTheme(darkTheme = false) {
                CatalogManagementScreen(onNavigateBack = {}, viewModel = vm)
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.catalog_mgmt_title)).assertIsDisplayed()
        composeRule.onNodeWithText("Paket Nasi Ayam Bakar").performScrollTo().assertExists()
        captureScreen("CatalogManagement/CatalogMain")
    }

    @Test
    fun storeSettingsScreen_switchesAllFourTabsCleanly() {
        val vm = StoreSettingsViewModel(
            repository = ShowcaseGovernanceRepo(),
            sessionManager = ShowcaseSessionManager(ownerSession())
        )
        composeRule.setContent {
            CassyTheme(darkTheme = false) {
                StoreSettingsScreen(
                    onNavigateBack = {},
                    viewModel = vm,
                    printerViewModel = showcasePrinterViewModel(),
                    displayViewModel = DisplayScreensViewModel(ShowcaseDisplayTokens())
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.settings_tab_midtrans)).assertIsDisplayed()
        captureScreen("StoreSettings/Tabs/MidtransTab")
        composeRule.onNodeWithText(context.getString(R.string.settings_tab_cms)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.cms_section_title)).assertIsDisplayed()
        captureScreen("StoreSettings/Tabs/CmsTab")
        composeRule.onNodeWithText(context.getString(R.string.settings_tab_service)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.service_section_title)).assertIsDisplayed()
        captureScreen("StoreSettings/Tabs/ServiceModeTab")
        composeRule.onNodeWithText(context.getString(R.string.settings_tab_printer)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.printer_tab_title)).assertIsDisplayed()
        composeRule.onNodeWithText("Struk kasir depan").assertExists()
        captureScreen("StoreSettings/Tabs/PrinterTab")
        composeRule.onNodeWithText(context.getString(R.string.printer_btn_add)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.printer_address_required)).assertExists()
        captureScreen("StoreSettings/Tabs/PrinterEditor", preferFullDisplay = true)
        composeRule.onNodeWithText(context.getString(R.string.btn_cancel)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.settings_tab_display)).performClick()
        composeRule.onNodeWithText("TV kasir depan").assertExists()
        captureScreen("StoreSettings/Tabs/DisplayTab")
        composeRule.onNodeWithText(context.getString(R.string.display_btn_add)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.display_field_name)).performTextInput("TV teras")
        composeRule.onNodeWithText(context.getString(R.string.display_btn_create)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.display_once_warning)).assertExists()
        captureScreen("StoreSettings/Tabs/DisplayCreated", preferFullDisplay = true)
    }

    @Test
    fun posTableBoard_rendersStatesAndRunningBill() {
        val rupiah = NumberFormat.getIntegerInstance(Locale("id", "ID"))
        val coffee = sampleProducts.first()
        val tables = listOf(
            PosTable("t-1", "M-01", "Meja A1", "Indoor", 2, "available", 0L),
            PosTable("t-2", "M-02", "Meja A2", "Indoor", 2, "occupied", 61_600L),
            PosTable("t-3", "M-03", "Sofa", "Indoor", 4, "settled", 0L),
            PosTable("t-4", "G-01", "Teras", "Garden", 4, "available", 0L),
            PosTable("t-5", "VIP-01", "Ruang VIP", "VIP", 8, "occupied", 245_000L)
        )
        composeRule.setContent {
            CassyTheme(darkTheme = false) {
                PosOrderSummaryPanel(
                    panelWidth = 420.dp,
                    tableName = "M-02",
                    orderType = "DINE-IN",
                    onToggleOrderType = {},
                    // One sent line from the open tab plus a new unsent line of the same product.
                    cart = listOf(CartItem(coffee, 2, isSentToKitchen = true), CartItem(coffee, 1)),
                    isCartLocked = false,
                    onIncreaseQuantity = {},
                    onDecreaseQuantity = {},
                    quote = OrderQuote(subtotalAmount = 84_000L, taxAmount = 8_400L, totalAmount = 92_400L),
                    rupiahFormat = rupiah,
                    onOpenDrawerKick = {},
                    onSendToKitchen = {},
                    onOpenCashModal = {},
                    onOpenQrisModal = {},
                    onOpenEdcModal = {},
                    failoverWarning = null,
                    onReprintStations = {},
                    tables = tables,
                    selectedTableId = "t-2"
                )
            }
        }
        composeRule.waitForIdle()
        captureScreen("PosTables/OpenBillCart", preferFullDisplay = true)
        composeRule.onNodeWithText(context.getString(R.string.order_table_tap_hint)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.order_table_board_title)).assertIsDisplayed()
        composeRule.onNodeWithText("VIP-01").assertIsDisplayed()
        captureScreen("PosTables/TableBoardPicker", preferFullDisplay = true)
    }

    @Test
    fun serviceRequestQueue_rendersPillAndWaitingCalls() {
        val requests = listOf(
            ServiceRequest("sr-1", "M-02", "Minta bill", null, "open", "2026-11-19T08:02:00Z"),
            ServiceRequest("sr-2", "VIP-01", "Air putih", "Tanpa es, 3 gelas", "acknowledged", "2026-11-19T08:05:00Z"),
            ServiceRequest("sr-3", "G-01", "Panggil pelayan", null, "open", "2026-11-19T08:09:00Z")
        )
        composeRule.setContent {
            CassyTheme(darkTheme = false) {
                PosServiceRequestPill(count = requests.size, onClick = {})
                PosServiceRequestsModal(
                    requests = requests,
                    busyRequestId = null,
                    onAcknowledge = {},
                    onDone = {},
                    onDismiss = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.service_panel_title)).assertIsDisplayed()
        composeRule.onNodeWithText("Tanpa es, 3 gelas").assertExists()
        captureScreen("PosServiceRequests/QueueModal", preferFullDisplay = true)
    }

    @Test
    fun tabItemVoid_rendersOwnerStepAndRemoteWait() {
        val owner = TabItemVoidUiState(
            tabId = "tab-1",
            itemId = "line-1",
            itemName = "Wagyu Rice Bowl",
            maxQuantity = 3,
            quantity = 2,
            reason = "Tamu batal pesan",
            pin = "482913",
            step = TabVoidStep.OWNER
        )
        val state = androidx.compose.runtime.mutableStateOf(owner)
        composeRule.setContent {
            CassyTheme(darkTheme = false) {
                PosTabItemVoidDialog(
                    state = state.value,
                    onQuantityChange = {},
                    onReasonChange = {},
                    onPinChange = {},
                    onOwnerPinChange = {},
                    onSubmit = {},
                    onAskOwner = {},
                    onDismiss = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.tab_void_owner_needed)).assertExists()
        captureScreen("PosTabItemVoid/OwnerNeeded", preferFullDisplay = true)

        state.value = owner.copy(step = TabVoidStep.WAITING)
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.tab_void_waiting)).assertExists()
        captureScreen("PosTabItemVoid/WaitingForOwner", preferFullDisplay = true)
    }

    @Test
    fun tabSplit_rendersGuestCountThenServerShares() {
        val rupiah = NumberFormat.getIntegerInstance(Locale("id", "ID"))
        val coffee = sampleProducts.first()
        var count by mutableIntStateOf(3)
        var splits by androidx.compose.runtime.mutableStateOf(emptyList<TabSplit>())
        composeRule.setContent {
            CassyTheme(darkTheme = false) {
                PosOrderSummaryPanel(
                    panelWidth = 420.dp,
                    tableName = "M-02",
                    orderType = "DINE-IN",
                    onToggleOrderType = {},
                    cart = listOf(CartItem(coffee, 3, isSentToKitchen = true)),
                    isCartLocked = false,
                    onIncreaseQuantity = {},
                    onDecreaseQuantity = {},
                    quote = OrderQuote(subtotalAmount = 84_000L, taxAmount = 8_400L, totalAmount = 92_400L),
                    rupiahFormat = rupiah,
                    onOpenDrawerKick = {},
                    onSendToKitchen = {},
                    onOpenCashModal = {},
                    onOpenQrisModal = {},
                    onOpenEdcModal = {},
                    failoverWarning = null,
                    onReprintStations = {},
                    selectedTableId = "t-2",
                    splitSection = {
                        PosTabSplitSection(
                            splitCount = count,
                            splits = splits,
                            selectedSplitId = "split-2",
                            isBusy = false,
                            rupiahFormat = rupiah,
                            onSplitCountChange = { count = it },
                            onSplit = {},
                            onSelectSplit = {}
                        )
                    }
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.tab_split_btn, 3)).assertExists()
        captureScreen("PosTabSplit/GuestCount", preferFullDisplay = true)

        splits = listOf(
            TabSplit("split-1", 1, 30_800L, 30_800L, "paid"),
            TabSplit("split-2", 2, 30_800L),
            TabSplit("split-3", 3, 30_800L)
        )
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.tab_split_progress, 1, 3)).assertExists()
        captureScreen("PosTabSplit/SharesOnePaid", preferFullDisplay = true)
    }

    @Test
    fun itemSplit_rendersGuestPickerPerUnit() {
        val units = listOf(
            SplitUnit("l-1", "Kopi Susu Gula Aren"),
            SplitUnit("l-1", "Kopi Susu Gula Aren"),
            SplitUnit("l-2", "Croissant Butter"),
            SplitUnit("l-3", "Nasi Ayam Bakar")
        )
        composeRule.setContent {
            CassyTheme(darkTheme = false) {
                PosItemSplitDialog(
                    draft = ItemSplitDraft(units, guests = 3, owners = listOf(1, 2, 2, null)),
                    isBusy = false,
                    onGuestsChange = {},
                    onAssign = { _, _ -> },
                    onConfirm = {},
                    onDismiss = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.tab_split_by_item_title)).assertExists()
        captureScreen("PosTabSplit/ByItemPicker", preferFullDisplay = true)
    }

    @Test
    fun shiftAndPosModals_andCatalogDialogsRenderOnDesignSystem() {
        val rupiah = NumberFormat.getIntegerInstance(Locale("id", "ID"))
        val drawers = listOf(CashDrawer("drw-1", "store-1", "Laci Utama", true))
        val shift = Shift("shf-1", "store-1", "drw-1", "u1", "Raka", 200_000L, "2026-11-14T08:00:00Z", expectedCash = 264_000L)
        val xReport = XReport("shf-1", "store-1", "drw-1", "Laci Utama", "u1", "Raka", "2026-11-14T08:00:00Z", "2026-11-14T12:00:00Z", summary = ShiftCashSummary(openingFloat = 200_000L, cashSales = 64_000L), expectedCash = 264_000L)
        val qris = PosPaymentAttempt("pay-1", "CASSY-QR-1", "qris", "pending", 35_200L, qrString = "00020101021226")

        var dialogStep by mutableIntStateOf(0)

        composeRule.setContent {
            CassyTheme(darkTheme = false) {
                when (dialogStep) {
                    0 -> OpenShiftModal(
                        drawers = drawers,
                        selectedDrawerId = "drw-1",
                        openingFloatInput = "200000",
                        notes = "",
                        isOpening = false,
                        onOpeningFloatChanged = {},
                        onNotesChanged = {},
                        onSubmit = {},
                        onDismiss = {}
                    )
                    1 -> CashMovementModal(
                        movementType = CashMovementType.PAID_OUT,
                        amountInput = "20000",
                        reason = "Es batu",
                        isRecording = false,
                        onMovementTypeChanged = {},
                        onAmountChanged = {},
                        onReasonChanged = {},
                        onSubmit = {},
                        onDismiss = {}
                    )
                    2 -> XReportModal(
                        report = xReport,
                        isLoading = false,
                        isPrinting = false,
                        onPrint = {},
                        onDismiss = {}
                    )
                    3 -> CloseShiftModal(
                        currentShift = shift,
                        countedCashInput = "264000",
                        varianceReason = "",
                        notes = "",
                        supervisorPinInput = "",
                        calculatedVariance = 0L,
                        isSupervisorAuthRequired = false,
                        printReceiptOnClose = true,
                        isClosing = false,
                        onCountedCashChanged = {},
                        onVarianceReasonChanged = {},
                        onNotesChanged = {},
                        onSupervisorPinChanged = {},
                        onPrintReceiptToggled = {},
                        onSubmit = {},
                        onDismiss = {}
                    )
                    4 -> PosCashModal(
                        grandTotal = 35_200L,
                        rupiahFormat = rupiah,
                        onDismiss = {},
                        onConfirmCashPayment = { _, _ -> }
                    )
                    5 -> PosQrisModal(
                        orderNumber = "ORD-2026-001",
                        payment = qris,
                        rupiahFormat = rupiah,
                        isCheckingStatus = false,
                        onCheckStatus = {},
                        onDismiss = {}
                    )
                    6 -> CategoryFormDialog(
                        initial = sampleCategories.first(),
                        onDismiss = {},
                        onSave = { _, _ -> }
                    )
                    else -> ProductFormDialog(
                        initial = sampleProducts.first(),
                        categories = sampleCategories,
                        isSubmitting = false,
                        onDismiss = {},
                        onSave = {}
                    )
                }
            }
        }

        val modalCaptures = listOf(
            R.string.shift_open_title to "ShiftManagement/Modals/OpenShiftModal",
            R.string.shift_movement_title to "ShiftManagement/Modals/CashMovementModal",
            R.string.shift_xreport_title to "ShiftManagement/Modals/XReportModal",
            R.string.shift_close_title to "ShiftManagement/Modals/CloseShiftModal",
            R.string.modal_cash_title to "PosLandscape/Modals/PosCashModal",
            R.string.modal_qris_title to "PosLandscape/Modals/PosQrisModal",
            R.string.catalog_edit_category to "CatalogManagement/Modals/CategoryFormDialog",
            R.string.catalog_edit_product to "CatalogManagement/Modals/ProductFormDialog"
        )
        modalCaptures.forEachIndexed { idx, (titleRes, path) ->
            composeRule.runOnIdle { dialogStep = idx }
            composeRule.waitForIdle()
            composeRule.onAllNodesWithText(context.getString(titleRes))[0].assertIsDisplayed()
            captureScreen(path, preferFullDisplay = true)
        }
    }

    @Test
    fun kdsDisplayLifecycleRendersTicketsWarningAndOfflineBanner() {
        val nowMillis = 1_795_200_000_000L
        val connectionFlow = MutableStateFlow(RealtimeConnectionState.CONNECTED)
        val eventsFlow = MutableSharedFlow<RealtimeEventEnvelope>(extraBufferCapacity = 4)
        val kdsSession = ownerSession().copy(
            permissions = ownerSession().permissions + listOf(
                KdsStation.KITCHEN.readPermission,
                KdsStation.KITCHEN.updatePermission,
                KdsStation.BAR.readPermission,
                KdsStation.BAR.updatePermission
            )
        )
        val initialTickets = mutableListOf(
            KdsTicket(
                id = "tkt-q1",
                orderId = "ord-101",
                orderNumber = "ORD-101",
                queueNumber = "A-041",
                tableNumber = "M-04",
                roundNumber = 1,
                station = KdsStation.KITCHEN,
                status = KdsTicketStatus.QUEUED,
                latePayment = false,
                createdAtMillis = nowMillis - 2 * 60_000L,
                lines = listOf(KdsTicketLine("Nasi Goreng Kampung", "Pedas", 2, listOf("Telur Mata Sapi"), "Tanpa acar"))
            ),
            KdsTicket(
                id = "tkt-p1",
                orderId = "ord-102",
                orderNumber = "ORD-102",
                queueNumber = "A-042",
                tableNumber = "M-07",
                roundNumber = 1,
                station = KdsStation.KITCHEN,
                status = KdsTicketStatus.PREPARING,
                latePayment = false,
                createdAtMillis = nowMillis - 12 * 60_000L,
                lines = listOf(KdsTicketLine("Ayam Bakar Madu", "Reguler", 1, listOf("Sambal Terasi"), ""))
            ),
            KdsTicket(
                id = "tkt-r1",
                orderId = "ord-103",
                orderNumber = "ORD-103",
                queueNumber = "A-040",
                tableNumber = "M-02",
                roundNumber = 1,
                station = KdsStation.KITCHEN,
                status = KdsTicketStatus.READY,
                latePayment = false,
                createdAtMillis = nowMillis - 7 * 60_000L,
                lines = listOf(KdsTicketLine("Pisang Goreng Keju", "Reguler", 1, emptyList(), ""))
            )
        )
        val fakeKdsRepo = object : KdsRepository {
            override suspend fun listTickets(station: KdsStation): Result<List<KdsTicket>> =
                Result.success(initialTickets.filter { it.station == station })

            override suspend fun transition(
                ticketId: String,
                to: KdsTicketStatus,
                expected: KdsTicketStatus
            ): Result<KdsTicket> {
                val idx = initialTickets.indexOfFirst { it.id == ticketId }
                val updated = initialTickets[idx].copy(status = to)
                initialTickets[idx] = updated
                return Result.success(updated)
            }
        }
        val fakeRealtime = object : RealtimeEventSource {
            override val connectionState: StateFlow<RealtimeConnectionState> = connectionFlow
            override val events: SharedFlow<RealtimeEventEnvelope> = eventsFlow
            override fun start(scope: CoroutineScope) = Unit
        }
        val kdsVm = KdsViewModel(
            repository = fakeKdsRepo,
            sessionManager = ShowcaseSessionManager(kdsSession),
            realtime = fakeRealtime,
            storeSettings = ShowcaseGovernanceRepo(),
            clock = flowOf(nowMillis)
        ).apply {
            selectStation(KdsStation.KITCHEN)
        }

        composeRule.setContent {
            CassyTheme(darkTheme = false) {
                KdsScreen(onNavigateBack = {}, viewModel = kdsVm)
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("A-041", substring = true).assertIsDisplayed()
        captureScreen("KdsDisplay/QueuedPreparingReady")

        composeRule.onNodeWithText("A-042", substring = true).assertIsDisplayed()
        captureScreen("KdsDisplay/LateTicketWarning")

        composeRule.runOnIdle {
            connectionFlow.value = RealtimeConnectionState.RECONNECTING
            kdsVm.recall("tkt-r1")
        }
        composeRule.waitForIdle()
        captureScreen("KdsDisplay/RecallAndOfflineBanner")
    }
}
