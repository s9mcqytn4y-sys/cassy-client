package com.azuredevid.cassy.ui.queue

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azuredevid.cassy.R
import com.azuredevid.cassy.ui.designsystem.CassyDimens
import com.azuredevid.cassy.ui.designsystem.CassyPrimaryButton
import com.azuredevid.cassy.ui.designsystem.CassyTextField
import org.koin.androidx.compose.koinViewModel

/**
 * 10-foot Android TV queue display showing "Sedang Disiapkan" and "Siap Diambil" order numbers
 * using the store's Pandan & Kunyit Material 3 palette.
 */
@Composable
fun TvQueueDisplayScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TvQueueDisplayViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(CassyDimens.SpaceLg)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.tv_queue_back)
                        )
                    }
                    Text(
                        text = state.storeName.ifBlank { stringResource(R.string.tv_queue_title) },
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (state.isPaired) {
                    IconButton(onClick = viewModel::refreshNow) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.tv_queue_refresh)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(CassyDimens.SpaceMd))

            if (!state.isPaired) {
                TvQueuePairingPane(
                    token = state.displayToken,
                    isLoading = state.isLoading,
                    error = state.error?.asString(),
                    onTokenChange = viewModel::setTokenInput,
                    onConnect = viewModel::pairAndStart
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceLg)
                ) {
                    TvQueueColumn(
                        title = stringResource(R.string.tv_queue_preparing),
                        numbers = state.preparingNumbers,
                        emptyText = stringResource(R.string.tv_queue_empty),
                        highlight = false,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                    TvQueueColumn(
                        title = stringResource(R.string.tv_queue_ready),
                        numbers = state.readyNumbers,
                        emptyText = stringResource(R.string.tv_queue_empty),
                        highlight = true,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
            }
        }
    }
}

@Composable
private fun TvQueuePairingPane(
    token: String,
    isLoading: Boolean,
    error: String?,
    onTokenChange: (String) -> Unit,
    onConnect: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(
            tonalElevation = 2.dp,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(0.6f)
        ) {
            Column(
                modifier = Modifier.padding(CassyDimens.SpaceXl),
                verticalArrangement = Arrangement.spacedBy(CassyDimens.SpaceMd)
            ) {
                Text(
                    text = stringResource(R.string.tv_queue_pair_heading),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                CassyTextField(
                    value = token,
                    onValueChange = onTokenChange,
                    label = stringResource(R.string.tv_queue_token_label),
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                CassyPrimaryButton(
                    text = stringResource(R.string.tv_queue_connect),
                    onClick = onConnect,
                    enabled = token.isNotBlank() && !isLoading,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun TvQueueColumn(
    title: String,
    numbers: List<String>,
    emptyText: String,
    highlight: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = if (highlight) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(modifier = Modifier.padding(CassyDimens.SpaceLg)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = if (highlight) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(CassyDimens.SpaceMd))
            if (numbers.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = emptyText,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 140.dp),
                    contentPadding = PaddingValues(vertical = CassyDimens.SpaceSm),
                    horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceMd),
                    verticalArrangement = Arrangement.spacedBy(CassyDimens.SpaceMd)
                ) {
                    items(numbers, key = { it }) { queueNumber ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = queueNumber,
                                    fontSize = 36.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (highlight) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
