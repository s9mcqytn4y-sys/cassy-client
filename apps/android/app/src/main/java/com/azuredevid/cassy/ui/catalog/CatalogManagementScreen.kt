package com.azuredevid.cassy.ui.catalog

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.azuredevid.cassy.R
import com.azuredevid.cassy.data.model.CatalogCategory
import com.azuredevid.cassy.data.model.CatalogProduct
import com.azuredevid.cassy.ui.catalog.components.CatalogProductManagementRow
import com.azuredevid.cassy.ui.catalog.components.CategoryFormDialog
import com.azuredevid.cassy.ui.catalog.components.CategorySidebarPanel
import com.azuredevid.cassy.ui.catalog.components.ProductFormDialog
import com.azuredevid.cassy.ui.catalog.components.VariantManagerDialog
import com.azuredevid.cassy.ui.designsystem.CassyDimens
import com.azuredevid.cassy.ui.designsystem.CassyEmptyState
import com.azuredevid.cassy.ui.designsystem.CassyErrorBanner
import com.azuredevid.cassy.ui.designsystem.CassyPrimaryButton
import com.azuredevid.cassy.ui.designsystem.CassyShimmerBox
import com.azuredevid.cassy.ui.designsystem.CassySnackbarHost
import com.azuredevid.cassy.ui.designsystem.CassyStatusBadge
import com.azuredevid.cassy.ui.designsystem.CassyTextField
import com.azuredevid.cassy.ui.designsystem.CassyToastTone
import com.azuredevid.cassy.ui.designsystem.showCassySnackbar
import org.koin.androidx.compose.koinViewModel
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogManagementScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CatalogManagementViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val rupiahFormat = remember { NumberFormat.getIntegerInstance(Locale("in", "ID")) }

    var showCategoryDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<CatalogCategory?>(null) }
    var showProductDialog by remember { mutableStateOf(false) }
    var editingProduct by remember { mutableStateOf<CatalogProduct?>(null) }
    var variantsTargetProductId by remember { mutableStateOf<String?>(null) }
    var pendingPhotoProductId by remember { mutableStateOf<String?>(null) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        val productId = pendingPhotoProductId
        pendingPhotoProductId = null
        if (uri != null && !productId.isNullOrBlank()) {
            viewModel.uploadProductImage(productId) {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is CatalogManagementUiEffect.ShowMessage -> {
                    snackbarHostState.showCassySnackbar(
                        message = effect.message.asString(context),
                        tone = effect.tone
                    )
                }
                CatalogManagementUiEffect.ProductSaved -> {
                    showProductDialog = false
                    editingProduct = null
                }
                CatalogManagementUiEffect.VariantSaved -> Unit
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { CassySnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.catalog_mgmt_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
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
                    IconButton(
                        onClick = { viewModel.refreshCatalog() },
                        modifier = Modifier.defaultMinSize(
                            minWidth = CassyDimens.TouchTargetMin,
                            minHeight = CassyDimens.TouchTargetMin
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.a11y_refresh)
                        )
                    }
                    if (state.canManageCatalog) {
                        CassyPrimaryButton(
                            text = stringResource(R.string.catalog_add_product),
                            icon = Icons.Rounded.Add,
                            onClick = {
                                editingProduct = null
                                showProductDialog = true
                            },
                            modifier = Modifier.padding(end = CassyDimens.SpaceMd)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            CategorySidebarPanel(
                categories = state.categories,
                selectedCategoryId = state.selectedCategoryId,
                canManageCatalog = state.canManageCatalog,
                onSelectCategory = viewModel::selectCategory,
                onAddCategory = {
                    editingCategory = null
                    showCategoryDialog = true
                },
                onEditCategory = { cat ->
                    editingCategory = cat
                    showCategoryDialog = true
                },
                onDeleteCategory = viewModel::deleteCategory
            )

            VerticalDivider(color = MaterialTheme.colorScheme.outline)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(CassyDimens.SpaceLg),
                verticalArrangement = Arrangement.spacedBy(CassyDimens.SpaceMd)
            ) {
                if (!state.canManageCatalog) {
                    CassyStatusBadge(
                        text = stringResource(R.string.catalog_readonly_hint),
                        tone = CassyToastTone.INFO
                    )
                }

                CassyTextField(
                    value = state.searchQuery,
                    onValueChange = viewModel::updateSearchQuery,
                    label = stringResource(R.string.catalog_search_hint),
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) }
                )

                if (state.isSubmitting || state.isUploadingImage) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                state.errorMessage?.let { err ->
                    CassyErrorBanner(
                        message = err.asString(),
                        onRetry = viewModel::refreshCatalog
                    )
                }

                when {
                    state.isLoading -> {
                        Column(verticalArrangement = Arrangement.spacedBy(CassyDimens.SpaceMd)) {
                            repeat(4) {
                                CassyShimmerBox(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(84.dp)
                                )
                            }
                        }
                    }
                    state.displayedProducts.isEmpty() -> {
                        CassyEmptyState(
                            icon = Icons.Rounded.Inventory2,
                            title = stringResource(R.string.catalog_empty),
                            subtitle = stringResource(R.string.catalog_readonly_hint),
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    else -> {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(CassyDimens.SpaceMd),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(state.displayedProducts, key = { it.id }) { product ->
                                CatalogProductManagementRow(
                                    product = product,
                                    rupiahFormat = rupiahFormat,
                                    canManage = state.canManageCatalog,
                                    isUploading = state.isUploadingImage,
                                    onToggleAvailability = { viewModel.toggleProductAvailability(product) },
                                    onEdit = {
                                        editingProduct = product
                                        showProductDialog = true
                                    },
                                    onUploadPhoto = {
                                        pendingPhotoProductId = product.id
                                        imagePicker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    onManageVariants = { variantsTargetProductId = product.id },
                                    onArchive = { viewModel.archiveProduct(product.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCategoryDialog) {
        CategoryFormDialog(
            initial = editingCategory,
            onDismiss = { showCategoryDialog = false },
            onSave = { name, order ->
                viewModel.saveCategory(editingCategory?.id, name, order, isActive = true)
                showCategoryDialog = false
            }
        )
    }

    if (showProductDialog) {
        ProductFormDialog(
            initial = editingProduct,
            categories = state.categories,
            isSubmitting = state.isSubmitting,
            onDismiss = { showProductDialog = false },
            onSave = { input ->
                viewModel.saveProduct(editingProduct?.id, input)
            }
        )
    }

    val variantProduct = state.products.find { it.id == variantsTargetProductId }
    if (variantProduct != null) {
        VariantManagerDialog(
            product = variantProduct,
            rupiahFormat = rupiahFormat,
            isSubmitting = state.isSubmitting,
            effects = viewModel.effects,
            onDismiss = { variantsTargetProductId = null },
            onSaveVariant = { editingVarId, input ->
                viewModel.saveVariant(variantProduct.id, editingVarId, input)
            },
            onArchiveVariant = { variantId -> viewModel.archiveVariant(variantId) }
        )
    }
}
