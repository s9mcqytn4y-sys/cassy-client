package com.azuredevid.cassy.ui.pairing

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.azuredevid.cassy.R
import com.azuredevid.cassy.ui.designsystem.CassyCard
import com.azuredevid.cassy.ui.designsystem.CassyDimens
import com.azuredevid.cassy.ui.designsystem.CassyErrorBanner
import com.azuredevid.cassy.ui.designsystem.CassyPrimaryButton
import com.azuredevid.cassy.ui.designsystem.CassySecondaryButton
import com.azuredevid.cassy.ui.designsystem.CassySnackbarHost
import com.azuredevid.cassy.ui.designsystem.CassySplitPane
import com.azuredevid.cassy.ui.designsystem.CassyStatusBadge
import com.azuredevid.cassy.ui.designsystem.CassyTextActionButton
import com.azuredevid.cassy.ui.designsystem.CassyTextField
import com.azuredevid.cassy.ui.designsystem.CassyToastTone
import com.azuredevid.cassy.ui.scanner.CameraQrScannerView
import org.koin.androidx.compose.koinViewModel

@Composable
fun StorePairingScreen(
    viewModel: StorePairingViewModel = koinViewModel(),
    onNavigateToServerConfig: () -> Unit = {},
    onPairingSuccess: (storeSlug: String, storeName: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.onEvent(StorePairingUiEvent.ToggleCamera(true))
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
        Box(modifier = Modifier.fillMaxSize()) {
            CameraQrScannerView(
                onQrDetected = { payload ->
                    viewModel.onEvent(StorePairingUiEvent.HandleQrPayload(payload))
                }
            )
            CassyCard(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(CassyDimens.SpaceXl),
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
            ) {
                Text(
                    text = stringResource(R.string.pairing_camera_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(CassyDimens.SpaceXs))
                Text(
                    text = stringResource(R.string.pairing_camera_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            CassyPrimaryButton(
                text = stringResource(R.string.btn_close_camera),
                onClick = { viewModel.onEvent(StorePairingUiEvent.ToggleCamera(false)) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = CassyDimens.Space2Xl)
            )
        }
        return
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { CassySnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        CassySplitPane(
            modifier = Modifier.padding(paddingValues),
            leftWeight = 0.44f,
            rightWeight = 0.56f,
            leftContent = {
                StorePairingBrandPane(onNavigateToServerConfig = onNavigateToServerConfig)
            },
            rightContent = {
                StorePairingFormPane(
                    state = state,
                    onOpenQrScanner = {
                        val granted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) {
                            viewModel.onEvent(StorePairingUiEvent.ToggleCamera(true))
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    onSlugChange = { viewModel.onEvent(StorePairingUiEvent.UpdateStoreSlug(it)) },
                    onEmailChange = { viewModel.onEvent(StorePairingUiEvent.UpdateEmail(it)) },
                    onPinChange = { viewModel.onEvent(StorePairingUiEvent.UpdatePin(it)) },
                    onSubmit = { viewModel.onEvent(StorePairingUiEvent.SubmitLogin) }
                )
            }
        )
    }
}

@Composable
private fun StorePairingBrandPane(onNavigateToServerConfig: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(CassyDimens.SpaceLg)) {
        CassyCard(contentPadding = CassyDimens.SpaceXl) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceMd)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_cassy_monogram),
                    contentDescription = stringResource(R.string.a11y_cassy_logo),
                    modifier = Modifier.size(56.dp)
                )
                Column {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.pairing_brand_tagline),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(CassyDimens.SpaceXl))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(CassyDimens.SpaceLg))

            Column(verticalArrangement = Arrangement.spacedBy(CassyDimens.SpaceMd)) {
                BrandHighlightRow(
                    icon = Icons.Rounded.Sync,
                    text = stringResource(R.string.pairing_highlight_1)
                )
                BrandHighlightRow(
                    icon = Icons.Rounded.Print,
                    text = stringResource(R.string.pairing_highlight_2)
                )
                BrandHighlightRow(
                    icon = Icons.Rounded.Lock,
                    text = stringResource(R.string.pairing_highlight_3)
                )
            }

            Spacer(Modifier.height(CassyDimens.SpaceXl))
            CassyTextActionButton(
                text = stringResource(R.string.auth_btn_server_config),
                icon = Icons.Rounded.Settings,
                onClick = onNavigateToServerConfig
            )
        }
    }
}

@Composable
private fun BrandHighlightRow(icon: ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceMd)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(CassyDimens.IconSizeMd)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun StorePairingFormPane(
    state: StorePairingUiState,
    onOpenQrScanner: () -> Unit,
    onSlugChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPinChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    val slugError = resolvePairingError(state.storeSlugError)
    val emailError = resolvePairingError(state.emailError)
    val pinError = resolvePairingError(state.pinError)
    val formError = resolvePairingError(state.formError)
    val qrInfo = if (state.qrScannedInfo == StorePairingViewModel.INFO_QR_SCANNED) {
        stringResource(R.string.auth_qr_scanned_hint)
    } else {
        state.qrScannedInfo
    }

    CassyCard(contentPadding = CassyDimens.SpaceXl) {
        Text(
            text = stringResource(R.string.pairing_screen_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(CassyDimens.SpaceXs))
        Text(
            text = stringResource(R.string.pairing_screen_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(CassyDimens.SpaceLg))

        CassySecondaryButton(
            text = stringResource(R.string.btn_scan_qr_camera),
            onClick = onOpenQrScanner,
            enabled = !state.isSubmitting,
            icon = Icons.Rounded.QrCodeScanner,
            modifier = Modifier.fillMaxWidth()
        )

        if (!qrInfo.isNullOrBlank()) {
            Spacer(Modifier.height(CassyDimens.SpaceMd))
            CassyStatusBadge(
                text = qrInfo,
                tone = CassyToastTone.SUCCESS
            )
        }

        Spacer(Modifier.height(CassyDimens.SpaceLg))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outline)
            Text(
                text = stringResource(R.string.or_divider),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = CassyDimens.SpaceMd)
            )
            HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outline)
        }

        Spacer(Modifier.height(CassyDimens.SpaceLg))

        CassyTextField(
            value = state.storeSlug,
            onValueChange = onSlugChange,
            label = stringResource(R.string.store_slug_label),
            isError = slugError != null,
            supportingText = slugError,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
        )

        Spacer(Modifier.height(CassyDimens.SpaceMd))

        CassyTextField(
            value = state.email,
            onValueChange = onEmailChange,
            label = stringResource(R.string.auth_email_label),
            isError = emailError != null,
            supportingText = emailError,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            )
        )

        Spacer(Modifier.height(CassyDimens.SpaceMd))

        CassyTextField(
            value = state.pin,
            onValueChange = onPinChange,
            label = stringResource(R.string.auth_pin_label),
            visualTransformation = PasswordVisualTransformation(),
            isError = pinError != null,
            supportingText = pinError,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { onSubmit() })
        )

        if (!formError.isNullOrBlank()) {
            Spacer(Modifier.height(CassyDimens.SpaceMd))
            CassyErrorBanner(message = formError, tone = CassyToastTone.ERROR)
        }

        Spacer(Modifier.height(CassyDimens.SpaceXl))

        CassyPrimaryButton(
            text = stringResource(R.string.auth_btn_sign_in),
            onClick = onSubmit,
            loading = state.isSubmitting,
            icon = Icons.AutoMirrored.Rounded.Login,
            height = CassyDimens.ButtonHeightTall,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun resolvePairingError(codeOrMessage: String?): String? {
    if (codeOrMessage.isNullOrBlank()) return null
    return when (codeOrMessage) {
        StorePairingViewModel.ERR_QR_EMPTY -> stringResource(R.string.pairing_err_qr_empty)
        StorePairingViewModel.ERR_QR_FORMAT -> stringResource(R.string.pairing_err_qr_format)
        StorePairingViewModel.ERR_SLUG_REQUIRED -> stringResource(R.string.auth_err_store_slug_required)
        StorePairingViewModel.ERR_EMAIL_REQUIRED -> stringResource(R.string.auth_err_email_required)
        StorePairingViewModel.ERR_PIN_INVALID -> stringResource(R.string.auth_err_pin_six_digits)
        StorePairingViewModel.ERR_AUTH_FAILED -> stringResource(R.string.pairing_err_auth_failed)
        else -> codeOrMessage
    }
}
