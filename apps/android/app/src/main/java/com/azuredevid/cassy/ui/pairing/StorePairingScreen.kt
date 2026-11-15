package com.azuredevid.cassy.ui.pairing

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.azuredevid.cassy.ui.designsystem.CassyDimens
import com.azuredevid.cassy.ui.designsystem.CassySnackbarHost
import com.azuredevid.cassy.ui.pairing.components.CameraPermissionDeniedDialog
import com.azuredevid.cassy.ui.pairing.components.StorePairingBrandPane
import com.azuredevid.cassy.ui.pairing.components.StorePairingFormPane
import com.azuredevid.cassy.ui.pairing.components.StorePairingHidDialog
import com.azuredevid.cassy.ui.scanner.CameraQrScannerView
import com.azuredevid.cassy.ui.theme.CassyPutihTulang
import org.koin.androidx.compose.koinViewModel

@Composable
fun StorePairingScreen(
    viewModel: StorePairingViewModel = koinViewModel(),
    onNavigateToServerConfig: () -> Unit = {},
    onPairingSuccess: (storeSlug: String, storeName: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val connectivity by viewModel.connectivityState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.onEvent(StorePairingUiEvent.ToggleCamera(true))
        } else {
            viewModel.onEvent(StorePairingUiEvent.CameraPermissionDenied)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is StorePairingUiEffect.PairingSucceeded -> {
                    onPairingSuccess(effect.storeSlug, effect.storeName)
                }
            }
        }
    }

    if (state.isCameraActive) {
        CameraQrScannerView(
            onQrDetected = { payload ->
                viewModel.onEvent(StorePairingUiEvent.HandleQrPayload(payload))
            },
            onCloseCamera = {
                viewModel.onEvent(StorePairingUiEvent.ToggleCamera(false))
            },
            onOpenHidScanner = {
                viewModel.onEvent(StorePairingUiEvent.OpenHidScanner(true))
            }
        )
        return
    }

    val openAppSettings: () -> Unit = {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    if (state.isHidScannerOpen) {
        StorePairingHidDialog(
            payloadInput = state.hidScannerInput,
            errorCodeOrMessage = state.hidError,
            onPayloadChange = { viewModel.onEvent(StorePairingUiEvent.UpdateHidInput(it)) },
            onApply = { viewModel.onEvent(StorePairingUiEvent.SubmitHidQrPayload) },
            onDismiss = { viewModel.onEvent(StorePairingUiEvent.OpenHidScanner(false)) }
        )
    } else if (state.cameraPermissionDenied) {
        CameraPermissionDeniedDialog(
            onOpenAppSettings = openAppSettings,
            onUseHidScanner = { viewModel.onEvent(StorePairingUiEvent.OpenHidScanner(true)) },
            onDismiss = { viewModel.onEvent(StorePairingUiEvent.ClearCameraPermissionDenied) }
        )
    }

    val openQrScanner: () -> Unit = {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.onEvent(StorePairingUiEvent.ToggleCamera(true))
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = CassyPutihTulang,
        snackbarHost = { CassySnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (maxWidth >= CassyDimens.SplitPaneBreakpoint) {
                Row(modifier = Modifier.fillMaxSize()) {
                    StorePairingBrandPane(
                        connectivity = connectivity,
                        onNavigateToServerConfig = onNavigateToServerConfig,
                        onRetryHealthProbe = { viewModel.onEvent(StorePairingUiEvent.RetryServerHealthProbe) },
                        fillFullHeight = true,
                        modifier = Modifier
                            .weight(0.44f)
                            .fillMaxHeight()
                    )
                    Box(
                        modifier = Modifier
                            .weight(0.56f)
                            .fillMaxHeight()
                            .background(CassyPutihTulang)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = CassyDimens.Space2Xl, vertical = CassyDimens.SpaceXl),
                        contentAlignment = Alignment.Center
                    ) {
                        StorePairingFormPane(
                            state = state,
                            onOpenQrScanner = openQrScanner,
                            onOpenHidDialog = { viewModel.onEvent(StorePairingUiEvent.OpenHidScanner(true)) },
                            onClearQrBadge = { viewModel.onEvent(StorePairingUiEvent.ClearQrScanned) },
                            onSlugChange = { viewModel.onEvent(StorePairingUiEvent.UpdateStoreSlug(it)) },
                            onEmailChange = { viewModel.onEvent(StorePairingUiEvent.UpdateEmail(it)) },
                            onPinChange = { viewModel.onEvent(StorePairingUiEvent.UpdatePin(it)) },
                            onTogglePinVisibility = { viewModel.onEvent(StorePairingUiEvent.TogglePinVisibility) },
                            onSubmit = { viewModel.onEvent(StorePairingUiEvent.SubmitLogin) }
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(CassyPutihTulang)
                        .verticalScroll(rememberScrollState())
                ) {
                    StorePairingBrandPane(
                        connectivity = connectivity,
                        onNavigateToServerConfig = onNavigateToServerConfig,
                        onRetryHealthProbe = { viewModel.onEvent(StorePairingUiEvent.RetryServerHealthProbe) },
                        fillFullHeight = false,
                        modifier = Modifier.fillMaxWidth()
                    )
                    StorePairingFormPane(
                        state = state,
                        onOpenQrScanner = openQrScanner,
                        onOpenHidDialog = { viewModel.onEvent(StorePairingUiEvent.OpenHidScanner(true)) },
                        onClearQrBadge = { viewModel.onEvent(StorePairingUiEvent.ClearQrScanned) },
                        onSlugChange = { viewModel.onEvent(StorePairingUiEvent.UpdateStoreSlug(it)) },
                        onEmailChange = { viewModel.onEvent(StorePairingUiEvent.UpdateEmail(it)) },
                        onPinChange = { viewModel.onEvent(StorePairingUiEvent.UpdatePin(it)) },
                        onTogglePinVisibility = { viewModel.onEvent(StorePairingUiEvent.TogglePinVisibility) },
                        onSubmit = { viewModel.onEvent(StorePairingUiEvent.SubmitLogin) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(CassyDimens.SpaceLg)
                    )
                }
            }
        }
    }
}
