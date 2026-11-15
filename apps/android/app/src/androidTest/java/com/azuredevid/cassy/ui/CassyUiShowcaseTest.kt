package com.azuredevid.cassy.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import com.azuredevid.cassy.domain.repository.MobileBootstrapData
import com.azuredevid.cassy.domain.repository.OrderQuoteRepository
import com.azuredevid.cassy.domain.repository.PosOrderRepository
import com.azuredevid.cassy.domain.repository.PrinterConfig
import com.azuredevid.cassy.domain.repository.PrinterSettingsRepository
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
import com.azuredevid.cassy.ui.theme.CassyTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

    private fun captureScreen(name: String, preferFullDisplay: Boolean = false) {
        composeRule.waitForIdle()
        runCatching {
            val fullDisplayBitmap = if (
                preferFullDisplay &&
                Build.VERSION.SDK_INT <= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
            ) {
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
        private var url: String = "https://demo.abbdullaziz.online"
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
        val vm = ServerConfigViewModel(ShowcaseServerSettingsRepo())
        composeRule.setContent {
            CassyTheme(darkTheme = false) {
                ServerConfigScreen(viewModel = vm, onNavigateBack = {})
            }
        }
        composeRule.onNodeWithText(context.getString(R.string.server_config_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.server_config_test_btn)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.server_config_preset_demo)).performClick()
        captureScreen("ServerConfig/ServerConfigDefault")
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
        composeRule.onNodeWithText(context.getString(R.string.btn_scan_qr_camera)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.auth_btn_sign_in)).assertIsDisplayed()
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
        composeRule.onNodeWithText(context.getString(R.string.pairing_server_status_server_unreachable))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.auth_err_store_slug_required)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.auth_btn_sign_in)).assertIsDisplayed()
        captureScreen("StorePairing/ValidationAndOffline")

        composeRule.runOnIdle {
            vm.onEvent(StorePairingUiEvent.CameraPermissionDenied)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.pairing_camera_permission_denied_title))
            .assertIsDisplayed()
        captureScreen("StorePairing/CameraPermissionModal", preferFullDisplay = true)

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
        captureScreen("StorePairing/HidScannerDialog", preferFullDisplay = true)
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
        captureScreen("StorePairing/QrCameraScanner", preferFullDisplay = true)
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
        composeRule.onNodeWithText(context.getString(R.string.btn_pay_cash)).assertIsDisplayed()
        captureScreen("PosLandscape/PosMain")
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
        composeRule.onNodeWithText("Paket Nasi Ayam Bakar").assertIsDisplayed()
        captureScreen("CatalogManagement/CatalogMain")
    }

    @Test
    fun storeSettingsScreen_switchesAllFourTabsCleanly() {
        val vm = StoreSettingsViewModel(
            repository = ShowcaseGovernanceRepo(),
            sessionManager = ShowcaseSessionManager(ownerSession()),
            printerSettings = ShowcasePrinterSettingsRepo()
        )
        composeRule.setContent {
            CassyTheme(darkTheme = false) {
                StoreSettingsScreen(onNavigateBack = {}, viewModel = vm)
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.settings_tab_midtrans)).assertIsDisplayed()
        captureScreen("StoreSettings/MidtransTab")
        composeRule.onNodeWithText(context.getString(R.string.settings_tab_cms)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.cms_preview_heading)).assertIsDisplayed()
        captureScreen("StoreSettings/CmsTab")
        composeRule.onNodeWithText(context.getString(R.string.settings_tab_service)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.service_section_title)).assertIsDisplayed()
        captureScreen("StoreSettings/ServiceModeTab")
        composeRule.onNodeWithText(context.getString(R.string.settings_tab_printer)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.printer_tab_title)).assertIsDisplayed()
        captureScreen("StoreSettings/PrinterTab")
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

        val expectedTitles = listOf(
            R.string.shift_open_title,
            R.string.shift_movement_title,
            R.string.shift_xreport_title,
            R.string.shift_close_title,
            R.string.modal_cash_title,
            R.string.modal_qris_title,
            R.string.catalog_edit_category,
            R.string.catalog_edit_product
        )
        expectedTitles.forEachIndexed { idx, titleRes ->
            composeRule.runOnIdle { dialogStep = idx }
            composeRule.waitForIdle()
            composeRule.onAllNodesWithText(context.getString(titleRes))[0].assertIsDisplayed()
        }
    }
}
