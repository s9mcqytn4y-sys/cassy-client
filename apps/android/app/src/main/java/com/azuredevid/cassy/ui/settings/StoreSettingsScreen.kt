package com.azuredevid.cassy.ui.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.azuredevid.cassy.R
import com.azuredevid.cassy.ui.designsystem.CassyDimens
import com.azuredevid.cassy.ui.designsystem.CassyEmptyState
import com.azuredevid.cassy.ui.designsystem.CassyShimmerBox
import com.azuredevid.cassy.ui.designsystem.CassySnackbarHost
import com.azuredevid.cassy.ui.designsystem.CassyStatusBadge
import com.azuredevid.cassy.ui.designsystem.CassyToastTone
import com.azuredevid.cassy.ui.designsystem.showCassySnackbar
import com.azuredevid.cassy.ui.settings.components.MidtransCredentialsTab
import com.azuredevid.cassy.ui.settings.components.CreatedDisplayDialog
import com.azuredevid.cassy.ui.settings.components.DisplayScreensTab
import com.azuredevid.cassy.ui.settings.components.NewDisplayDialog
import com.azuredevid.cassy.ui.settings.components.PrinterEditorDialog
import com.azuredevid.cassy.ui.settings.components.RevokeDisplayDialog
import com.azuredevid.cassy.ui.settings.components.PrinterSettingsTab
import com.azuredevid.cassy.ui.settings.components.StoreBrandCmsTab
import com.azuredevid.cassy.ui.settings.components.StoreFeatureFlagsTab
import com.azuredevid.cassy.ui.settings.components.StoreServiceModeTab
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreSettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StoreSettingsViewModel = koinViewModel(),
    printerViewModel: PrinterSettingsViewModel = koinViewModel(),
    displayViewModel: DisplayScreensViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val printerState by printerViewModel.uiState.collectAsState()
    val printerTabOpen = state.selectedTabIndex == 3
    val displayState by displayViewModel.uiState.collectAsState()
    LaunchedEffect(state.selectedTabIndex == 4, state.hasManageAccess) {
        if (state.selectedTabIndex == 4 && state.hasManageAccess) displayViewModel.load()
    }
    // The server list is fetched when the tab opens, after the role is known.
    LaunchedEffect(printerTabOpen, state.hasManageAccess, state.isLoading) {
        if (printerTabOpen && !state.isLoading) printerViewModel.load(state.hasManageAccess)
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        printerViewModel.refreshBluetoothDevices()
    }
    val colors = MaterialTheme.colorScheme

    // Single source of snackbar truth from one-shot effects to avoid double-firing.
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is StoreSettingsUiEffect.ShowSnackbar -> {
                    snackbarHostState.showCassySnackbar(
                        message = effect.message.asString(context),
                        tone = if (effect.isError) CassyToastTone.ERROR else CassyToastTone.SUCCESS
                    )
                    viewModel.onEvent(StoreSettingsUiEvent.DismissMessage)
                }
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.background,
        snackbarHost = { CassySnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.settings_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.settings_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.defaultMinSize(
                            minWidth = CassyDimens.TouchTargetMin,
                            minHeight = CassyDimens.TouchTargetMin
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.a11y_navigate_back)
                        )
                    }
                },
                actions = {
                    CassyStatusBadge(
                        text = stringResource(
                            if (state.hasManageAccess) {
                                R.string.settings_access_badge_allowed
                            } else {
                                R.string.settings_access_badge_denied
                            }
                        ),
                        tone = if (state.hasManageAccess) CassyToastTone.SUCCESS else CassyToastTone.WARNING,
                        modifier = Modifier.padding(end = CassyDimens.SpaceLg)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val tabs = listOf(
                stringResource(R.string.settings_tab_midtrans),
                stringResource(R.string.settings_tab_cms),
                stringResource(R.string.settings_tab_service),
                stringResource(R.string.settings_tab_printer),
                stringResource(R.string.settings_tab_display),
                stringResource(R.string.settings_tab_features)
            )

            androidx.compose.foundation.layout.BoxWithConstraints(
                modifier = Modifier.fillMaxWidth()
            ) {
                val isWide = maxWidth >= CassyDimens.SplitPaneBreakpoint
                val tabContent: @Composable () -> Unit = {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = state.selectedTabIndex == index,
                            onClick = { viewModel.onEvent(StoreSettingsUiEvent.SelectTab(index)) },
                            text = {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (state.selectedTabIndex == index) {
                                        FontWeight.Bold
                                    } else {
                                        FontWeight.Normal
                                    },
                                    color = if (state.selectedTabIndex == index) {
                                        colors.onSurface
                                    } else {
                                        colors.onSurfaceVariant
                                    }
                                )
                            },
                            modifier = Modifier.defaultMinSize(minHeight = CassyDimens.TouchTargetMin)
                        )
                    }
                }
                if (isWide) {
                    PrimaryTabRow(
                        selectedTabIndex = state.selectedTabIndex,
                        containerColor = colors.surface,
                        contentColor = colors.onSurface,
                        indicator = {
                            TabRowDefaults.PrimaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(state.selectedTabIndex),
                                color = colors.primary
                            )
                        },
                        tabs = tabContent
                    )
                } else {
                    androidx.compose.material3.PrimaryScrollableTabRow(
                        selectedTabIndex = state.selectedTabIndex,
                        containerColor = colors.surface,
                        contentColor = colors.onSurface,
                        edgePadding = CassyDimens.SpaceMd,
                        indicator = {
                            TabRowDefaults.PrimaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(state.selectedTabIndex),
                                color = colors.primary
                            )
                        },
                        tabs = tabContent
                    )
                }
            }

            if (state.isLoading) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(CassyDimens.SpaceXl),
                    verticalArrangement = Arrangement.spacedBy(CassyDimens.SpaceMd)
                ) {
                    Text(
                        text = stringResource(R.string.settings_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant
                    )
                    repeat(3) {
                        CassyShimmerBox(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(104.dp)
                        )
                    }
                }
            } else {
                when (state.selectedTabIndex) {
                    0 -> if (state.hasManageAccess) {
                        MidtransCredentialsTab(
                            state = state,
                            onEvent = viewModel::onEvent,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        RestrictedAccessNotice(modifier = Modifier.fillMaxSize())
                    }
                    1 -> if (state.hasManageAccess) {
                        StoreBrandCmsTab(
                            state = state,
                            onEvent = viewModel::onEvent,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        RestrictedAccessNotice(modifier = Modifier.fillMaxSize())
                    }
                    2 -> StoreServiceModeTab(
                        state = state,
                        onEvent = viewModel::onEvent,
                        modifier = Modifier.fillMaxSize()
                    )
                    3 -> PrinterSettingsTab(
                        state = printerState,
                        onAdd = printerViewModel::openNew,
                        onEdit = printerViewModel::openEdit,
                        onTest = printerViewModel::test,
                        modifier = Modifier.fillMaxSize()
                    )
                    4 -> DisplayScreensTab(
                        state = displayState,
                        onAdd = displayViewModel::openNew,
                        onRevoke = displayViewModel::askRevoke,
                        modifier = Modifier.fillMaxSize()
                    )
                    5 -> if (state.hasManageAccess) {
                        StoreFeatureFlagsTab(
                            state = state,
                            onEvent = viewModel::onEvent,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        RestrictedAccessNotice(modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }

    displayState.newName?.let { name ->
        NewDisplayDialog(
            name = name,
            isBusy = displayState.isBusy,
            error = displayState.message?.asString(),
            onName = displayViewModel::setName,
            onCreate = displayViewModel::create,
            onDismiss = displayViewModel::dismissNew
        )
    }
    displayState.created?.let { CreatedDisplayDialog(created = it, onDismiss = displayViewModel::dismissCreated) }
    displayState.revoking?.let { screen ->
        RevokeDisplayDialog(
            screen = screen,
            isBusy = displayState.isBusy,
            onConfirm = displayViewModel::confirmRevoke,
            onDismiss = displayViewModel::cancelRevoke
        )
    }

    printerState.editor?.let { editor ->
        PrinterEditorDialog(
            editor = editor,
            isSaving = printerState.isSaving,
            error = printerState.message?.asString(),
            pairedBluetoothDevices = printerState.pairedBluetoothDevices,
            hasBluetoothPermission = printerState.hasBluetoothPermission,
            isBluetoothEnabled = printerState.isBluetoothEnabled,
            onName = printerViewModel::setName,
            onStation = printerViewModel::setStation,
            onTransport = printerViewModel::setTransportType,
            onAddress = printerViewModel::setLanAddress,
            onBluetoothMac = printerViewModel::setBluetoothMac,
            onSelectPairedDevice = printerViewModel::selectPairedDevice,
            onRefreshBluetooth = printerViewModel::refreshBluetoothDevices,
            onRequestBluetoothPermission = {
                val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
                } else {
                    arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.ACCESS_FINE_LOCATION)
                }
                bluetoothPermissionLauncher.launch(permissions)
            },
            onOpenBluetoothSettings = {
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                }
            },
            onPaper = printerViewModel::setPaperWidth,
            onSave = printerViewModel::save,
            onDismiss = printerViewModel::dismissEditor
        )
    }
}

@Composable
private fun RestrictedAccessNotice(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.padding(CassyDimens.SpaceXl),
        contentAlignment = Alignment.Center
    ) {
        CassyEmptyState(
            icon = Icons.Rounded.Lock,
            title = stringResource(R.string.settings_access_denied_title),
            subtitle = stringResource(R.string.settings_access_denied_body)
        )
    }
}
