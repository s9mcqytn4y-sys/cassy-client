package com.azuredevid.cassy.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.azuredevid.cassy.BuildConfig
import com.azuredevid.cassy.R
import com.azuredevid.cassy.ui.designsystem.CassyCard
import com.azuredevid.cassy.ui.designsystem.CassyDimens
import com.azuredevid.cassy.ui.designsystem.CassyErrorBanner
import com.azuredevid.cassy.ui.designsystem.CassyFeedbackCue
import com.azuredevid.cassy.ui.designsystem.CassyPrimaryButton
import com.azuredevid.cassy.ui.designsystem.CassySecondaryButton
import com.azuredevid.cassy.ui.designsystem.CassySectionHeader
import com.azuredevid.cassy.ui.designsystem.CassySnackbarHost
import com.azuredevid.cassy.ui.designsystem.CassySplitPane
import com.azuredevid.cassy.ui.designsystem.CassyTextField
import com.azuredevid.cassy.ui.designsystem.CassyToastTone
import com.azuredevid.cassy.ui.designsystem.showCassySnackbar
import com.azuredevid.cassy.ui.settings.components.ConnectionDiagnosticsBox
import com.azuredevid.cassy.ui.settings.components.EnvironmentSecurityBadge
import com.azuredevid.cassy.ui.settings.components.MdnsDiscoveryDialog
import com.azuredevid.cassy.ui.settings.components.ServerConfigInfoPane
import com.azuredevid.cassy.ui.theme.CassyPandan
import org.koin.androidx.compose.koinViewModel

private object ServerEndpointPresets {
    private const val STAGING_HTTPS_URL = "https://api.abbdullaziz.online"
    const val LOCAL_STORE_LAN_HTTP_URL = "http://192.168.1.100:8080"
    const val LOCAL_STORE_LAN_TLS_URL = "https://pos-lokal.cassy.local"
    const val EMULATOR_LOCAL_URL = "http://10.0.2.2:8080"

    fun cloudPresetUrl(isProd: Boolean): String =
        if (isProd || BuildConfig.BASE_API_URL.startsWith("https://")) {
            BuildConfig.BASE_API_URL
        } else {
            STAGING_HTTPS_URL
        }

    fun lanPresetUrl(isProd: Boolean): String =
        if (isProd) LOCAL_STORE_LAN_TLS_URL else LOCAL_STORE_LAN_HTTP_URL
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ServerConfigScreen(
    viewModel: ServerConfigViewModel = koinViewModel(),
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val view = LocalView.current
    val probeOkTemplate = stringResource(R.string.server_config_test_ok)
    val probeFailTemplate = stringResource(R.string.server_config_test_fail)

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ServerConfigUiEffect.ShowProbeResult -> {
                    val msg = if (effect.success) {
                        String.format(probeOkTemplate, effect.detail.asString(view.context))
                    } else {
                        String.format(probeFailTemplate, effect.detail.asString(view.context))
                    }
                    snackbarHostState.showCassySnackbar(
                        message = msg,
                        tone = if (effect.success) CassyToastTone.SUCCESS else CassyToastTone.ERROR
                    )
                }
            }
        }
    }

    if (state.showMdnsDialog) {
        MdnsDiscoveryDialog(
            isDiscovering = state.isDiscoveringMdns,
            discoveredServers = state.discoveredServers,
            onSelectServer = { url ->
                CassyFeedbackCue.tap(view)
                viewModel.selectDiscoveredServer(url)
            },
            onRescan = {
                CassyFeedbackCue.tap(view)
                viewModel.discoverLocalServers(forceRescan = true)
            },
            onDismiss = viewModel::dismissMdnsDialog
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { CassySnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.server_config_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            CassyFeedbackCue.tap(view)
                            onNavigateBack()
                        },
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
                    EnvironmentSecurityBadge(enforceHttpsOnly = state.enforceHttpsOnly)
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        androidx.compose.foundation.layout.BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            val isSplitPane = maxWidth >= CassyDimens.SplitPaneBreakpoint
            CassySplitPane(
                modifier = Modifier.fillMaxSize(),
                leftWeight = 0.42f,
                rightWeight = 0.58f,
                leftContent = {
                    ServerConfigInfoPane(
                        state = state,
                        isSplitPane = isSplitPane,
                        onDiscoverMdns = {
                            CassyFeedbackCue.tap(view)
                            viewModel.discoverLocalServers()
                        }
                    )
                },
                rightContent = {
                    ServerConfigFormPane(
                        state = state,
                        isSplitPane = isSplitPane,
                        onUrlChanged = viewModel::onUrlChanged,
                        onClearUrl = {
                            CassyFeedbackCue.tap(view)
                            viewModel.clearUrl()
                        },
                        onApplyPreset = { presetUrl ->
                            CassyFeedbackCue.tap(view)
                            viewModel.applyPreset(presetUrl)
                        },
                        onTestConnection = {
                            CassyFeedbackCue.tap(view)
                            viewModel.testConnection()
                        },
                        onSave = {
                            if (viewModel.save()) {
                                CassyFeedbackCue.success(view)
                                onNavigateBack()
                            } else {
                                CassyFeedbackCue.warningOrError(view)
                            }
                        }
                    )
                }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.ServerConfigFormPane(
    state: ServerConfigUiState,
    isSplitPane: Boolean,
    onUrlChanged: (String) -> Unit,
    onClearUrl: () -> Unit,
    onApplyPreset: (String) -> Unit,
    onTestConnection: () -> Unit,
    onSave: () -> Unit
) {
    val errMessage = when {
        state.isCleartextBlockedInProd -> stringResource(R.string.server_config_prod_cleartext_error)
        state.isUrlInvalid || state.urlError != null -> stringResource(R.string.server_config_err_url)
        else -> null
    }

    val isProd = state.enforceHttpsOnly
    val cloudPresetUrl = ServerEndpointPresets.cloudPresetUrl(isProd)
    val lanPresetUrl = ServerEndpointPresets.lanPresetUrl(isProd)

    val cardModifier = if (isSplitPane) {
        Modifier
            .fillMaxWidth()
            .fillMaxHeight()
    } else {
        Modifier.fillMaxWidth()
    }
    val innerColumnModifier = if (isSplitPane) {
        Modifier
            .fillMaxWidth()
            .weight(1f)
            .verticalScroll(rememberScrollState())
    } else {
        Modifier.fillMaxWidth()
    }

    CassyCard(
        modifier = cardModifier
    ) {
        Column(
            modifier = innerColumnModifier
        ) {
            CassySectionHeader(title = stringResource(R.string.server_config_url_label))
            Spacer(Modifier.height(CassyDimens.SpaceMd))

            Text(
                text = stringResource(R.string.server_config_presets_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(CassyDimens.SpaceSm))

            val chipColors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = Color(0xFFB7DEC8),
                selectedLabelColor = CassyPandan
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceSm),
                verticalArrangement = Arrangement.spacedBy(CassyDimens.SpaceSm)
            ) {
                FilterChip(
                    selected = state.urlInput.trimEnd('/') == cloudPresetUrl.trimEnd('/'),
                    onClick = { onApplyPreset(cloudPresetUrl) },
                    label = { Text(stringResource(R.string.server_config_preset_demo)) },
                    colors = chipColors,
                    modifier = Modifier.defaultMinSize(minHeight = CassyDimens.TouchTargetMin)
                )
                FilterChip(
                    selected = state.urlInput.trimEnd('/') == lanPresetUrl.trimEnd('/'),
                    onClick = { onApplyPreset(lanPresetUrl) },
                    label = { Text(stringResource(R.string.server_config_preset_lan)) },
                    colors = chipColors,
                    modifier = Modifier.defaultMinSize(minHeight = CassyDimens.TouchTargetMin)
                )
                if (BuildConfig.DEBUG || !isProd) {
                    FilterChip(
                        selected = state.urlInput.trimEnd('/') == ServerEndpointPresets.EMULATOR_LOCAL_URL,
                        onClick = { onApplyPreset(ServerEndpointPresets.EMULATOR_LOCAL_URL) },
                        label = { Text(stringResource(R.string.server_config_preset_emulator)) },
                        colors = chipColors,
                        modifier = Modifier.defaultMinSize(minHeight = CassyDimens.TouchTargetMin)
                    )
                }
            }

            Spacer(Modifier.height(CassyDimens.SpaceMd))

            CassyTextField(
                value = state.urlInput,
                onValueChange = onUrlChanged,
                label = stringResource(R.string.server_config_url_label),
                isError = errMessage != null,
                supportingText = errMessage,
                trailingIcon = if (state.urlInput.isNotEmpty()) {
                    {
                        IconButton(
                            onClick = onClearUrl,
                            modifier = Modifier.size(CassyDimens.TouchTargetMin)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.server_config_clear_url_cd),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    null
                }
            )

            if (state.showCleartextWarning && errMessage == null) {
                Spacer(Modifier.height(CassyDimens.SpaceSm))
                CassyErrorBanner(
                    message = stringResource(R.string.server_config_cleartext_warning),
                    tone = CassyToastTone.WARNING
                )
            }

            Spacer(Modifier.height(CassyDimens.SpaceMd))

            ConnectionDiagnosticsBox(state = state)
        }

        Spacer(Modifier.height(CassyDimens.SpaceMd))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(CassyDimens.SpaceMd))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceMd)
        ) {
            CassySecondaryButton(
                text = if (state.isTesting) {
                    stringResource(R.string.server_config_testing)
                } else {
                    stringResource(R.string.server_config_test_btn)
                },
                onClick = onTestConnection,
                loading = state.isTesting,
                icon = Icons.Rounded.NetworkCheck,
                borderColor = CassyPandan,
                modifier = Modifier.weight(1f)
            )
            CassyPrimaryButton(
                text = stringResource(R.string.server_config_save),
                onClick = onSave,
                icon = Icons.Rounded.Save,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
