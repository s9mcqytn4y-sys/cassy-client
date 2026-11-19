package com.azuredevid.cassy.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.azuredevid.cassy.core.security.SessionManager
import com.azuredevid.cassy.ui.catalog.CatalogManagementScreen
import com.azuredevid.cassy.ui.kds.KdsScreen
import com.azuredevid.cassy.ui.pairing.StorePairingScreen
import com.azuredevid.cassy.ui.pos.PosLandscapeScreen
import com.azuredevid.cassy.ui.settings.ServerConfigScreen
import com.azuredevid.cassy.ui.settings.StoreSettingsScreen
import com.azuredevid.cassy.ui.tables.TableManagementScreen
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.azuredevid.cassy.R
import com.azuredevid.cassy.ui.designsystem.CassyDimens
import com.azuredevid.cassy.ui.theme.statusWarnFg
import org.koin.androidx.compose.koinViewModel

/** The owner moved this tablet to KDS; it stays on the cashier until the shift closes and orders sync. */
@Composable
private fun RoleSwitchHeldBanner() {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.device_role_held_banner),
            style = MaterialTheme.typography.bodyMedium,
            color = statusWarnFg(),
            modifier = Modifier.padding(horizontal = CassyDimens.SpaceLg, vertical = CassyDimens.SpaceSm)
        )
    }
}

@Composable
fun CassyNavHost(
    navController: NavHostController,
    startDestination: CassyRoute,
    sessionManager: SessionManager,
    onOpenCashDrawer: () -> Unit,
    onShowPairingToast: (storeName: String) -> Unit,
    modifier: Modifier = Modifier,
    roleViewModel: DeviceRoleViewModel = koinViewModel()
) {
    val activeSession by sessionManager.sessionFlow.collectAsState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val roleState by roleViewModel.uiState.collectAsState()

    // The owner can change this tablet's role at any time; check it whenever the app comes back.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) roleViewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(activeSession != null) {
        if (activeSession != null) roleViewModel.refresh()
    }

    // RoleGate: a kds tablet lives on the KDS board, any other role on the cashier.
    LaunchedEffect(roleState, currentRoute, activeSession != null) {
        if (activeSession == null || currentRoute == null) return@LaunchedEffect
        val onPos = currentRoute.contains("PosCashier")
        val onKdsAsHome = currentRoute.contains("KdsBoard") && navController.previousBackStackEntry == null
        when {
            roleState.home == DeviceHome.KDS && !roleState.held && onPos -> navController.navigate(CassyRoute.KdsBoard) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
            roleState.home == DeviceHome.POS && onKdsAsHome -> navController.navigate(CassyRoute.PosCashier) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    // Fail-closed: if session becomes invalid/cleared while inside POS or Settings, return to Pairing.
    LaunchedEffect(activeSession, currentRoute) {
        val isPublicAuthRoute = currentRoute?.contains("StorePairing") == true ||
            currentRoute?.contains("ServerConfig") == true
        if (activeSession == null && currentRoute != null && !isPublicAuthRoute) {
            navController.navigate(CassyRoute.StorePairing) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable<CassyRoute.StorePairing> {
            StorePairingScreen(
                onNavigateToServerConfig = {
                    navController.navigate(CassyRoute.ServerConfig)
                },
                onPairingSuccess = { _, storeName ->
                    onShowPairingToast(storeName)
                    navController.navigate(CassyRoute.PosCashier) {
                        popUpTo(CassyRoute.StorePairing) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable<CassyRoute.ServerConfig> {
            ServerConfigScreen(
                onNavigateBack = { navController.navigateUp() }
            )
        }

        composable<CassyRoute.PosCashier> {
            Column {
                if (roleState.home == DeviceHome.KDS && roleState.held) RoleSwitchHeldBanner()
            PosLandscapeScreen(
                modifier = Modifier.weight(1f),
                onOpenDrawerKick = onOpenCashDrawer,
                onNavigateToSettings = {
                    navController.navigate(CassyRoute.StoreSettings)
                },
                onNavigateToCatalog = {
                    navController.navigate(CassyRoute.CatalogManagement)
                },
                onNavigateToKds = {
                    navController.navigate(CassyRoute.KdsBoard)
                },
                onNavigateToTables = {
                    navController.navigate(CassyRoute.TableManagement)
                },
                onNavigateToPairing = {
                    navController.navigate(CassyRoute.StorePairing)
                }
            )
            }
        }

        composable<CassyRoute.StoreSettings> {
            StoreSettingsScreen(
                onNavigateBack = { navController.navigateUp() }
            )
        }

        composable<CassyRoute.CatalogManagement> {
            CatalogManagementScreen(
                onNavigateBack = { navController.navigateUp() }
            )
        }

        composable<CassyRoute.KdsBoard> {
            KdsScreen(
                onNavigateBack = if (navController.previousBackStackEntry != null) {
                    { navController.navigateUp() }
                } else {
                    null
                }
            )
        }

        composable<CassyRoute.TableManagement> {
            TableManagementScreen(
                onNavigateBack = { navController.navigateUp() }
            )
        }
    }
}
