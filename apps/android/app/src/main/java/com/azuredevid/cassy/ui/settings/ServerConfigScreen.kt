package com.azuredevid.cassy.ui.settings

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import org.koin.androidx.compose.koinViewModel

private const val DEMO_HTTPS_URL = "https://demo.abbdullaziz.online"
private const val EMULATOR_LOCAL_URL = "http://10.0.2.2:8080"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ServerConfigScreen(
    viewModel: ServerConfigViewModel = koinViewModel(),
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val probeOkTemplate = stringResource(R.string.server_config_test_ok)
    val probeFailTemplate = stringResource(R.string.server_config_test_fail)

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                ServerConfigUiEffect.SavedAndNavigateBack -> onNavigateBack()
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
                ServerConfigInfoPane(state = state)
            },
            rightContent = {
                ServerConfigFormPane(
                    state = state,
                    onUrlChanged = viewModel::onUrlChanged,
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
private fun ServerConfigInfoPane(state: ServerConfigUiState) {
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

        CassyCard(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)) {
            CassySectionHeader(title = stringResource(R.string.server_config_guide_title))
            Spacer(Modifier.height(CassyDimens.SpaceSm))
            Text(
                text = stringResource(R.string.server_config_guide_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (state.probeStatusOk != null) {
                Spacer(Modifier.height(CassyDimens.SpaceMd))
                val msg = if (state.probeStatusOk) {
                    stringResource(R.string.server_config_test_ok, "HTTP ${state.probeHttpCode ?: 200}")
                } else {
                    stringResource(
                        R.string.server_config_test_fail,
                        state.probeErrorMessage ?: "-"
                    )
                }
                CassyStatusBadge(
                    text = msg,
                    tone = if (state.probeStatusOk) CassyToastTone.SUCCESS else CassyToastTone.ERROR,
                    icon = if (state.probeStatusOk) Icons.Rounded.CheckCircle else null
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ServerConfigFormPane(
    state: ServerConfigUiState,
    onUrlChanged: (String) -> Unit,
    onApplyPreset: (String) -> Unit,
    onTestConnection: () -> Unit,
    onSave: () -> Unit
) {
    val errMessage = if (state.isUrlInvalid || state.urlError != null) {
        stringResource(R.string.server_config_err_url)
    } else null

    CassyCard {
        CassySectionHeader(title = stringResource(R.string.server_config_url_label))
        Spacer(Modifier.height(CassyDimens.SpaceMd))

        Text(
            text = stringResource(R.string.server_config_presets_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(CassyDimens.SpaceSm))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceSm),
            verticalArrangement = Arrangement.spacedBy(CassyDimens.SpaceSm)
        ) {
            if (BuildConfig.DEBUG) {
                FilterChip(
                    selected = state.urlInput.trimEnd('/') == DEMO_HTTPS_URL,
                    onClick = { onApplyPreset(DEMO_HTTPS_URL) },
                    label = { Text(stringResource(R.string.server_config_preset_demo)) },
                    modifier = Modifier.defaultMinSize(minHeight = CassyDimens.TouchTargetMin)
                )
                FilterChip(
                    selected = state.urlInput.trimEnd('/') == EMULATOR_LOCAL_URL,
                    onClick = { onApplyPreset(EMULATOR_LOCAL_URL) },
                    label = { Text(stringResource(R.string.server_config_preset_emulator)) },
                    modifier = Modifier.defaultMinSize(minHeight = CassyDimens.TouchTargetMin)
                )
            }
            if (BuildConfig.BASE_API_URL !in listOf(DEMO_HTTPS_URL, EMULATOR_LOCAL_URL) || !BuildConfig.DEBUG) {
                FilterChip(
                    selected = state.urlInput.trimEnd('/') == BuildConfig.BASE_API_URL,
                    onClick = { onApplyPreset(BuildConfig.BASE_API_URL) },
                    label = { Text(BuildConfig.BASE_API_URL) },
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
            supportingText = errMessage
        )

        if (state.probeStatusOk == false && !state.probeErrorMessage.isNullOrBlank()) {
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
