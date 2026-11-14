package com.azuredevid.cassy.ui.settings

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
import com.azuredevid.cassy.ui.settings.components.PrinterSettingsTab
import com.azuredevid.cassy.ui.settings.components.StoreBrandCmsTab
import com.azuredevid.cassy.ui.settings.components.StoreServiceModeTab
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreSettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StoreSettingsViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val colors = MaterialTheme.colorScheme

    // Single source of snackbar truth from one-shot effects to avoid double-firing.
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is StoreSettingsUiEffect.ShowSnackbar -> {
                    snackbarHostState.showCassySnackbar(
                        message = effect.message,
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
                stringResource(R.string.settings_tab_printer)
            )

            PrimaryTabRow(
                selectedTabIndex = state.selectedTabIndex,
                containerColor = colors.surface,
                contentColor = colors.onSurface,
                indicator = {
                    TabRowDefaults.PrimaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(state.selectedTabIndex),
                        color = colors.primary
                    )
                }
            ) {
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
                    2 -> if (state.hasManageAccess) {
                        StoreServiceModeTab(
                            state = state,
                            onEvent = viewModel::onEvent,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        RestrictedAccessNotice(modifier = Modifier.fillMaxSize())
                    }
                    3 -> PrinterSettingsTab(
                        state = state,
                        onEvent = viewModel::onEvent,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
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
