package com.azuredevid.cassy.ui.kds

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SoupKitchen
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.azuredevid.cassy.R
import com.azuredevid.cassy.domain.model.KdsTicket
import com.azuredevid.cassy.domain.model.KdsTicketStatus
import com.azuredevid.cassy.ui.designsystem.CassyDimens
import com.azuredevid.cassy.ui.designsystem.CassyEmptyState
import com.azuredevid.cassy.ui.designsystem.CassyErrorBanner
import com.azuredevid.cassy.ui.designsystem.CassySnackbarHost
import com.azuredevid.cassy.ui.designsystem.showCassySnackbar
import com.azuredevid.cassy.ui.kds.components.KdsConnectionBanner
import com.azuredevid.cassy.ui.kds.components.KdsStationSelector
import com.azuredevid.cassy.ui.kds.components.KdsTicketCard
import com.azuredevid.cassy.ui.kds.components.labelRes
import org.koin.androidx.compose.koinViewModel

/**
 * Kitchen Display System (Release One PR 8). Three columns, Queued, Preparing, Ready, oldest ticket on
 * top. A persistent banner shows when live updates stop; snackbars only report actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KdsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: KdsViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is KdsUiEffect.ShowMessage -> snackbarHostState.showCassySnackbar(
                    message = when (val m = effect.message) {
                        is KdsMessage.Res -> context.getString(m.id)
                        is KdsMessage.Server -> m.text
                    },
                    tone = effect.tone
                )
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { CassySnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.station?.let { stringResource(R.string.kds_title_station, stringResource(it.labelRes())) }
                            ?: stringResource(R.string.kds_title)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.a11y_navigate_back))
                    }
                },
                actions = {
                    KdsStationSelector(
                        stations = state.availableStations,
                        selected = state.station,
                        onSelect = viewModel::selectStation,
                        modifier = Modifier.padding(end = CassyDimens.SpaceSm)
                    )
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.a11y_refresh))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(CassyDimens.SpaceLg),
            verticalArrangement = Arrangement.spacedBy(CassyDimens.SpaceMd)
        ) {
            if (state.station != null) {
                KdsConnectionBanner(state = state.connection, onRefresh = viewModel::refresh)
            }
            state.loadError?.let { error ->
                CassyErrorBanner(
                    message = when (error) {
                        is KdsMessage.Res -> stringResource(error.id)
                        is KdsMessage.Server -> error.text
                    },
                    onRetry = if (state.station != null) viewModel::refresh else null
                )
            }
            if (state.station == null) return@Column
            if (!state.isLoading && state.tickets.isEmpty() && state.loadError == null) {
                CassyEmptyState(
                    icon = Icons.Rounded.SoupKitchen,
                    title = stringResource(R.string.kds_empty_title),
                    subtitle = stringResource(R.string.kds_empty_subtitle)
                )
                return@Column
            }
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceLg)
            ) {
                listOf(
                    KdsTicketStatus.QUEUED to R.string.kds_column_queued,
                    KdsTicketStatus.PREPARING to R.string.kds_column_preparing,
                    KdsTicketStatus.READY to R.string.kds_column_ready
                ).forEach { (status, titleRes) ->
                    KdsColumn(
                        title = stringResource(titleRes),
                        tickets = state.ticketsIn(status),
                        state = state,
                        onBump = viewModel::bump,
                        onRecall = viewModel::recall
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.KdsColumn(
    title: String,
    tickets: List<KdsTicket>,
    state: KdsUiState,
    onBump: (String) -> Unit,
    onRecall: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(CassyDimens.SpaceSm)
    ) {
        Text(
            text = stringResource(R.string.kds_column_title, title, tickets.size),
            style = MaterialTheme.typography.titleLarge.merge(TextStyle(fontFeatureSettings = "tnum")),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(CassyDimens.SpaceMd)
        ) {
            items(tickets, key = { it.id }) { ticket ->
                KdsTicketCard(
                    ticket = ticket,
                    nowMillis = state.nowMillis,
                    serviceMode = state.serviceMode,
                    canBump = state.canBump,
                    isPending = ticket.id in state.pendingTicketIds,
                    onBump = { onBump(ticket.id) },
                    onRecall = { onRecall(ticket.id) }
                )
            }
        }
    }
}
