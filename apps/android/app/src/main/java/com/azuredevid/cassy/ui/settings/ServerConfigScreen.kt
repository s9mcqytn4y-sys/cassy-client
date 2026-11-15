package com.azuredevid.cassy.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.azuredevid.cassy.BuildConfig
import com.azuredevid.cassy.R
import com.azuredevid.cassy.ui.designsystem.CassyCard
import com.azuredevid.cassy.ui.designsystem.CassyDimens
import com.azuredevid.cassy.ui.designsystem.CassyErrorBanner
import com.azuredevid.cassy.ui.designsystem.CassyPrimaryButton
import com.azuredevid.cassy.ui.designsystem.CassySecondaryButton
import com.azuredevid.cassy.ui.designsystem.CassySectionHeader
import com.azuredevid.cassy.ui.designsystem.CassySnackbarHost
import com.azuredevid.cassy.ui.designsystem.CassySplitPane
import com.azuredevid.cassy.ui.designsystem.CassyStatusBadge
import com.azuredevid.cassy.ui.designsystem.CassyTextField
import com.azuredevid.cassy.ui.designsystem.CassyToastTone
import com.azuredevid.cassy.ui.designsystem.showCassySnackbar
import com.azuredevid.cassy.ui.theme.CassyPandan
import org.koin.androidx.compose.koinViewModel

private const val DEMO_HTTPS_URL = "https://demo.abbdullaziz.online"
private const val LOCAL_STORE_LAN_HTTP_URL = "http://192.168.1.100:8080"
private const val LOCAL_STORE_LAN_TLS_URL = "https://pos-lokal.cassy.local"
private const val EMULATOR_LOCAL_URL = "http://10.0.2.2:8080"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ServerConfigScreen(
    viewModel: ServerConfigViewModel = koinViewModel(),
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val probeOkTemplate = stringResource(R.string.server_config_test_ok)
    val probeFailTemplate = stringResource(R.string.server_config_test_fail)

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ServerConfigUiEffect.ShowProbeResult -> {
                    val msg = if (effect.success) {
                        String.format(probeOkTemplate, effect.detail)
                    } else {
                        String.format(probeFailTemplate, effect.detail)
                    }
                    snackbarHostState.showCassySnackbar(
                        message = msg,
                        tone = if (effect.success) CassyToastTone.SUCCESS else CassyToastTone.ERROR
                    )
                }
            }
        }
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        CassySplitPane(
            modifier = Modifier.padding(paddingValues),
            leftWeight = 0.42f,
            rightWeight = 0.58f,
            leftContent = {
                ServerConfigInfoPane(
                    state = state,
                    onDiscoverMdns = viewModel::discoverLocalServers,
                    onSelectDiscoveredServer = viewModel::applyPreset
                )
            },
            rightContent = {
                ServerConfigFormPane(
                    state = state,
                    onUrlChanged = viewModel::onUrlChanged,
                    onClearUrl = viewModel::clearUrl,
                    onApplyPreset = viewModel::applyPreset,
                    onTestConnection = viewModel::testConnection,
                    onSave = {
                        if (viewModel.save()) {
                            onNavigateBack()
                        }
                    }
                )
            }
        )
    }
}

@Composable
private fun ServerConfigInfoPane(
    state: ServerConfigUiState,
    onDiscoverMdns: () -> Unit,
    onSelectDiscoveredServer: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(CassyDimens.SpaceLg)) {
        CassyCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceMd)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_cassy_monogram),
                    contentDescription = stringResource(R.string.a11y_cassy_logo),
                    modifier = Modifier.size(44.dp)
                )
                Column {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.pairing_brand_tagline),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(CassyDimens.SpaceLg))
            Text(
                text = stringResource(R.string.server_config_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(CassyDimens.SpaceMd))
            CassyStatusBadge(
                text = BuildConfig.FLAVOR.uppercase(),
                tone = CassyToastTone.INFO,
                icon = Icons.Rounded.Dns
            )
        }

        CassyCard(containerColor = Color(0xFFECE9DF)) {
            CassySectionHeader(title = stringResource(R.string.server_config_guide_title))
            Spacer(Modifier.height(CassyDimens.SpaceSm))

            GuideBulletItem(
                title = stringResource(R.string.server_config_guide_cloud_title),
                description = stringResource(R.string.server_config_guide_cloud_desc)
            )
            Spacer(Modifier.height(CassyDimens.SpaceXs))
            GuideBulletItem(
                title = stringResource(R.string.server_config_guide_lan_title),
                description = stringResource(R.string.server_config_guide_lan_desc)
            )
            Spacer(Modifier.height(CassyDimens.SpaceXs))
            GuideBulletItem(
                title = stringResource(R.string.server_config_guide_emulator_title),
                description = stringResource(R.string.server_config_guide_emulator_desc)
            )

            Spacer(Modifier.height(CassyDimens.SpaceMd))

            CassySecondaryButton(
                text = if (state.isDiscoveringMdns) {
                    stringResource(R.string.server_config_discovering_mdns)
                } else {
                    stringResource(R.string.server_config_discover_mdns)
                },
                onClick = onDiscoverMdns,
                loading = state.isDiscoveringMdns,
                icon = Icons.Rounded.TravelExplore,
                modifier = Modifier.fillMaxWidth()
            )

            if (state.discoveredServers.isNotEmpty()) {
                Spacer(Modifier.height(CassyDimens.SpaceSm))
                Column(verticalArrangement = Arrangement.spacedBy(CassyDimens.SpaceXs)) {
                    for (server in state.discoveredServers) {
                        FilterChip(
                            selected = state.urlInput.trimEnd('/') == server.baseUrl.trimEnd('/'),
                            onClick = { onSelectDiscoveredServer(server.baseUrl) },
                            label = {
                                Text(
                                    stringResource(
                                        R.string.server_config_mdns_found_format,
                                        server.serviceName,
                                        server.baseUrl
                                    )
                                )
                            },
                            modifier = Modifier.defaultMinSize(minHeight = CassyDimens.TouchTargetMin)
                        )
                    }
                }
            } else if (state.mdnsDiscoveryCompleted && !state.isDiscoveringMdns) {
                Spacer(Modifier.height(CassyDimens.SpaceSm))
                Text(
                    text = stringResource(R.string.server_config_mdns_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun GuideBulletItem(
    title: String,
    description: String
) {
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)) {
                append("• ")
                append(title)
                append(" ")
            }
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                append(description)
            }
        },
        style = MaterialTheme.typography.bodyMedium
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ServerConfigFormPane(
    state: ServerConfigUiState,
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

    val isProd = BuildConfig.FLAVOR.equals("prod", ignoreCase = true)
    val cloudPresetUrl = if (isProd) BuildConfig.BASE_API_URL else DEMO_HTTPS_URL
    val lanPresetUrl = if (isProd) LOCAL_STORE_LAN_TLS_URL else LOCAL_STORE_LAN_HTTP_URL

    CassyCard {
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
                    selected = state.urlInput.trimEnd('/') == EMULATOR_LOCAL_URL,
                    onClick = { onApplyPreset(EMULATOR_LOCAL_URL) },
                    label = { Text(stringResource(R.string.server_config_preset_emulator)) },
                    colors = chipColors,
                    modifier = Modifier.defaultMinSize(minHeight = CassyDimens.TouchTargetMin)
                )
            }
        }

        Spacer(Modifier.height(CassyDimens.SpaceLg))

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

        if (state.probeStatusOk == true) {
            Spacer(Modifier.height(CassyDimens.SpaceMd))
            val statusCode = state.probeHttpCode ?: 200
            val latency = state.probeLatencyMs ?: 1L
            val diagText = if (state.probeIsTls) {
                stringResource(R.string.server_config_status_connected_tls_format, statusCode, latency)
            } else {
                stringResource(R.string.server_config_status_connected_lan_format, statusCode, latency)
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(CassyDimens.RadiusButton),
                color = Color(0xFFE6F4EA),
                border = BorderStroke(1.dp, Color(0xFF4CAF50))
            ) {
                Row(
                    modifier = Modifier.padding(
                        horizontal = CassyDimens.SpaceMd,
                        vertical = CassyDimens.SpaceMd
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceSm)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF1B5E20),
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = diagText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF17211E)
                    )
                }
            }
        } else if (state.probeStatusOk == false && !state.probeErrorMessage.isNullOrBlank()) {
            Spacer(Modifier.height(CassyDimens.SpaceMd))
            CassyErrorBanner(
                message = stringResource(R.string.server_config_test_fail, state.probeErrorMessage),
                tone = CassyToastTone.ERROR
            )
        }

        Spacer(Modifier.height(CassyDimens.SpaceXl))

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
