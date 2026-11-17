package com.azuredevid.cassy.di

import com.azuredevid.cassy.core.hardware.PrinterPort9100Manager
import com.azuredevid.cassy.core.hardware.ShiftReportThermalBuilder
import com.azuredevid.cassy.core.security.AeadCipher
import com.azuredevid.cassy.core.security.AndroidTinkAeadCipher
import com.azuredevid.cassy.core.security.BiometricSecurityManager
import com.azuredevid.cassy.core.security.DataStoreSessionManager
import com.azuredevid.cassy.core.security.SessionManager
import com.azuredevid.cassy.data.network.AndroidNsdLocalServerDiscovery
import com.azuredevid.cassy.data.network.AndroidServerHealthMonitor
import com.azuredevid.cassy.data.network.CassyHttpClientFactory
import com.azuredevid.cassy.data.network.CassyRealtimeClient
import com.azuredevid.cassy.data.repository.DataStorePrinterSettingsRepository
import com.azuredevid.cassy.data.repository.DataStoreServerSettingsRepository
import com.azuredevid.cassy.data.repository.KtorAuthRepository
import com.azuredevid.cassy.data.repository.KtorCatalogRepository
import com.azuredevid.cassy.data.repository.KtorKdsRepository
import com.azuredevid.cassy.data.repository.KtorOrderQuoteRepository
import com.azuredevid.cassy.data.repository.KtorPosOrderRepository
import com.azuredevid.cassy.data.repository.KtorShiftRepository
import com.azuredevid.cassy.data.repository.KtorStoreGovernanceRepository
import com.azuredevid.cassy.data.repository.KtorTableManagementRepository
import com.azuredevid.cassy.data.repository.ServerSettingsRepository
import com.azuredevid.cassy.domain.repository.AuthRepository
import com.azuredevid.cassy.domain.repository.CatalogRepository
import com.azuredevid.cassy.domain.repository.KdsRepository
import com.azuredevid.cassy.domain.repository.RealtimeEventSource
import com.azuredevid.cassy.domain.repository.LocalServerDiscoveryRepository
import com.azuredevid.cassy.domain.repository.OrderQuoteRepository
import com.azuredevid.cassy.domain.repository.PosOrderRepository
import com.azuredevid.cassy.domain.repository.PrinterSettingsRepository
import com.azuredevid.cassy.domain.repository.ServerHealthMonitor
import com.azuredevid.cassy.domain.repository.ShiftRepository
import com.azuredevid.cassy.domain.repository.StoreGovernanceRepository
import com.azuredevid.cassy.domain.repository.TableManagementRepository
import com.azuredevid.cassy.ui.catalog.CatalogManagementViewModel
import com.azuredevid.cassy.ui.kds.KdsViewModel
import com.azuredevid.cassy.ui.pairing.StorePairingViewModel
import com.azuredevid.cassy.ui.pos.PosViewModel
import com.azuredevid.cassy.ui.settings.ServerConfigViewModel
import com.azuredevid.cassy.ui.settings.StoreSettingsViewModel
import com.azuredevid.cassy.ui.shift.ShiftViewModel
import com.azuredevid.cassy.ui.tables.TableManagementViewModel
import io.ktor.client.HttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single<PrinterPort9100Manager> { PrinterPort9100Manager() }
    single<ShiftReportThermalBuilder> { ShiftReportThermalBuilder() }
    single<BiometricSecurityManager> { BiometricSecurityManager(androidContext()) }
    single<AeadCipher> { AndroidTinkAeadCipher(androidContext()) }
    single<SessionManager> { DataStoreSessionManager(androidContext(), get()) }
    single<ServerSettingsRepository> { DataStoreServerSettingsRepository(androidContext()) }
    single<PrinterSettingsRepository> { DataStorePrinterSettingsRepository(androidContext()) }
    single<LocalServerDiscoveryRepository> { AndroidNsdLocalServerDiscovery(androidContext()) }

    single<HttpClient> {
        CassyHttpClientFactory.create(
            sessionManager = get(),
            serverSettings = get()
        )
    }

    single<CassyRealtimeClient> {
        CassyRealtimeClient(
            httpClient = get(),
            serverSettings = get(),
            sessionManager = get()
        )
    }
    single<RealtimeEventSource> { get<CassyRealtimeClient>() }

    single<ServerHealthMonitor> {
        AndroidServerHealthMonitor(
            context = androidContext(),
            httpClient = get(),
            serverSettings = get()
        )
    }
}

val repositoryModule = module {
    single<AuthRepository> {
        KtorAuthRepository(
            httpClient = get(),
            serverSettings = get(),
            sessionManager = get()
        )
    }

    single<StoreGovernanceRepository> {
        KtorStoreGovernanceRepository(
            httpClient = get(),
            serverSettings = get()
        )
    }

    single<CatalogRepository> {
        KtorCatalogRepository(
            httpClient = get(),
            serverSettings = get()
        )
    }

    single<OrderQuoteRepository> {
        val sessionManager = get<SessionManager>()
        KtorOrderQuoteRepository(
            httpClient = get(),
            serverSettings = get(),
            staffToken = { sessionManager.getAccessToken() }
        )
    }

    single<PosOrderRepository> {
        KtorPosOrderRepository(
            httpClient = get(),
            serverSettings = get()
        )
    }

    single<ShiftRepository> {
        KtorShiftRepository(
            httpClient = get(),
            serverSettings = get()
        )
    }

    single<KdsRepository> {
        KtorKdsRepository(
            httpClient = get(),
            serverSettings = get()
        )
    }

    single<TableManagementRepository> {
        KtorTableManagementRepository(
            httpClient = get(),
            serverSettings = get()
        )
    }
}

val viewModelModule = module {
    viewModel {
        StorePairingViewModel(
            authRepository = get(),
            healthMonitor = get(),
            savedStateHandle = get()
        )
    }
    viewModel {
        ServerConfigViewModel(
            serverSettings = get(),
            httpClient = get(),
            healthMonitor = get(),
            localDiscovery = get(),
            savedStateHandle = get()
        )
    }
    viewModel {
        StoreSettingsViewModel(
            repository = get(),
            sessionManager = get(),
            printerSettings = get(),
            printerManager = get(),
            savedStateHandle = get()
        )
    }
    viewModel {
        ShiftViewModel(
            shiftRepository = get(),
            printerSettings = get(),
            sessionManager = get(),
            printerManager = get(),
            thermalBuilder = get(),
            savedStateHandle = get()
        )
    }
    viewModel {
        PosViewModel(
            catalogRepository = get(),
            orderQuoteRepository = get(),
            posOrderRepository = get(),
            printerSettings = get(),
            sessionManager = get(),
            printerManager = get(),
            realtimeClient = get(),
            savedStateHandle = get()
        )
    }
    viewModel {
        CatalogManagementViewModel(
            catalogRepository = get(),
            sessionManager = get(),
            savedStateHandle = get()
        )
    }
    viewModel {
        TableManagementViewModel(
            repository = get(),
            sessionManager = get(),
            printerSettings = get(),
            printerManager = get(),
            savedStateHandle = get()
        )
    }
    viewModel {
        KdsViewModel(
            repository = get(),
            sessionManager = get(),
            realtime = get(),
            storeSettings = get(),
            savedStateHandle = get()
        )
    }
}
