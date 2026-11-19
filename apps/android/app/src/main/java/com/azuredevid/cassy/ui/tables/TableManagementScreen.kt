package com.azuredevid.cassy.ui.tables

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.TableRestaurant
import androidx.compose.material.icons.rounded.TouchApp
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.azuredevid.cassy.R
import com.azuredevid.cassy.core.hardware.TableQrPosterCopy
import com.azuredevid.cassy.ui.designsystem.CassyDimens
import com.azuredevid.cassy.ui.designsystem.CassyEmptyState
import com.azuredevid.cassy.ui.designsystem.CassyErrorBanner
import com.azuredevid.cassy.ui.designsystem.CassyPrimaryButton
import com.azuredevid.cassy.ui.designsystem.CassySectionHeader
import com.azuredevid.cassy.ui.designsystem.CassyShimmerBox
import com.azuredevid.cassy.ui.designsystem.CassySnackbarHost
import com.azuredevid.cassy.ui.designsystem.CassySplitPane
import com.azuredevid.cassy.ui.designsystem.showCassySnackbar
import com.azuredevid.cassy.ui.tables.components.TableCard
import com.azuredevid.cassy.ui.tables.components.TableConfirmDialog
import com.azuredevid.cassy.ui.tables.components.TableFormDialog
import com.azuredevid.cassy.ui.tables.components.TableQrPanel
import org.koin.androidx.compose.koinViewModel

private const val LIST_WEIGHT = 0.68f
private const val PANEL_WEIGHT = 0.32f

/**
 * Table and QR management (Release One PR 5). Tablet layout splits 68:32: the table grid on the left,
 * the selected table's QR and actions on the right; below the split breakpoint the panes stack.
 * All decisions live in [TableManagementViewModel]; this screen only renders state and routes events.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TableManagementScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TableManagementViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var showForm by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmRotateId by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmArchiveId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingPdfId by rememberSaveable { mutableStateOf<String?>(null) }

    val posterCopy = TableQrPosterCopy(
        heading = stringResource(R.string.tables_poster_heading),
        scanHint = stringResource(R.string.tables_poster_scan_hint),
        footer = stringResource(R.string.tables_poster_footer)
    )
    val pdfHeading = stringResource(R.string.tables_poster_heading)
    val pdfScanHint = stringResource(R.string.tables_poster_scan_hint)

    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val id = pendingPdfId
        pendingPdfId = null
        if (uri != null && id != null) {
            viewModel.exportTentCard(id, pdfHeading, pdfScanHint) { context.contentResolver.openOutputStream(uri) }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is TableManagementUiEffect.ShowMessage -> snackbarHostState.showCassySnackbar(
                    message = when (val m = effect.message) {
                        is TableMessage.Res -> if (m.arg == null) context.getString(m.id) else context.getString(m.id, m.arg)
                        is TableMessage.Server -> m.text
                    },
                    tone = effect.tone
                )
                TableManagementUiEffect.FormSaved -> {
                    showForm = false
                    editingId = null
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
                title = { Text(stringResource(R.string.tables_title)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.padding(CassyDimens.SpaceXxs)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.a11y_navigate_back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh, enabled = !state.isBusy) {
                        Icon(imageVector = Icons.Rounded.Refresh, contentDescription = stringResource(R.string.a11y_refresh))
                    }
                    if (state.canManageTables) {
                        CassyPrimaryButton(
                            text = stringResource(R.string.tables_btn_add),
                            icon = Icons.Rounded.Add,
                            onClick = {
                                editingId = null
                                showForm = true
                            },
                            enabled = !state.isBusy,
                            modifier = Modifier.padding(end = CassyDimens.SpaceMd)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        CassySplitPane(
            modifier = Modifier.padding(innerPadding),
            leftWeight = LIST_WEIGHT,
            rightWeight = PANEL_WEIGHT,
            leftContent = {
                CassySectionHeader(
                    title = stringResource(R.string.tables_list_title, state.tables.size),
                    subtitle = stringResource(R.string.tables_list_subtitle)
                )
                state.loadError?.let { error ->
                    CassyErrorBanner(
                        message = when (error) {
                            is TableMessage.Res -> stringResource(error.id)
                            is TableMessage.Server -> error.text
                        },
                        onRetry = viewModel::refresh,
                        modifier = Modifier.padding(top = CassyDimens.SpaceMd)
                    )
                }
                when {
                    state.isLoading && state.tables.isEmpty() -> repeat(3) {
                        CassyShimmerBox(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(96.dp)
                                .padding(top = CassyDimens.SpaceMd)
                        )
                    }
                    state.tables.isEmpty() && state.loadError == null -> CassyEmptyState(
                        icon = Icons.Rounded.TableRestaurant,
                        title = stringResource(R.string.tables_empty_title),
                        subtitle = stringResource(R.string.tables_empty_subtitle),
                        actionLabel = if (state.canManageTables) stringResource(R.string.tables_btn_add) else null,
                        onAction = if (state.canManageTables) ({ editingId = null; showForm = true }) else null
                    )
                    else -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 200.dp),
                        horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceMd),
                        verticalArrangement = Arrangement.spacedBy(CassyDimens.SpaceMd),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = CassyDimens.SpaceMd)
                    ) {
                        items(state.tables, key = { it.id }) { table ->
                            TableCard(
                                table = table,
                                isSelected = table.id == state.selectedTableId,
                                onClick = { viewModel.selectTable(table.id) }
                            )
                        }
                    }
                }
            },
            rightContent = {
                val selected = state.selectedTable
                if (selected == null) {
                    CassyEmptyState(
                        icon = Icons.Rounded.TouchApp,
                        title = stringResource(R.string.tables_panel_empty_title),
                        subtitle = stringResource(R.string.tables_panel_empty_subtitle)
                    )
                } else {
                    TableQrPanel(
                        table = selected,
                        qrUrl = state.selectedQrUrl,
                        canManageTables = state.canManageTables,
                        isBusy = state.isBusy,
                        isPrinting = state.isPrinting,
                        isExporting = state.isExporting,
                        onPrintPoster = { viewModel.printPoster(selected.id, posterCopy) },
                        onExportPdf = {
                            pendingPdfId = selected.id
                            pdfLauncher.launch(context.getString(R.string.tables_pdf_file_name, selected.displayName))
                        },
                        onRotateQr = { confirmRotateId = selected.id },
                        onEdit = {
                            editingId = selected.id
                            showForm = true
                        },
                        onArchive = { confirmArchiveId = selected.id }
                    )
                }
            }
        )
    }

    if (showForm) {
        TableFormDialog(
            initial = state.tables.firstOrNull { it.id == editingId },
            isSubmitting = state.isSubmitting,
            onDismiss = {
                showForm = false
                editingId = null
            },
            onSave = { draft -> viewModel.saveTable(editingId, draft) }
        )
    }

    confirmRotateId?.let { id ->
        val name = state.tables.firstOrNull { it.id == id }?.displayName.orEmpty()
        TableConfirmDialog(
            title = stringResource(R.string.tables_rotate_title, name),
            body = stringResource(R.string.tables_rotate_body),
            confirmLabel = stringResource(R.string.tables_btn_rotate_confirm),
            cancelLabel = stringResource(R.string.tables_btn_cancel),
            icon = Icons.Rounded.QrCode2,
            onConfirm = {
                confirmRotateId = null
                viewModel.confirmRotateQr(id)
            },
            onDismiss = { confirmRotateId = null }
        )
    }

    confirmArchiveId?.let { id ->
        val name = state.tables.firstOrNull { it.id == id }?.displayName.orEmpty()
        TableConfirmDialog(
            title = stringResource(R.string.tables_archive_title, name),
            body = stringResource(R.string.tables_archive_body),
            confirmLabel = stringResource(R.string.tables_btn_archive),
            cancelLabel = stringResource(R.string.tables_btn_cancel),
            icon = Icons.Rounded.Archive,
            onConfirm = {
                confirmArchiveId = null
                viewModel.archiveTable(id)
            },
            onDismiss = { confirmArchiveId = null }
        )
    }
}
