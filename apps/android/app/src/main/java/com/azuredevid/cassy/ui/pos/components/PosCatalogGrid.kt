package com.azuredevid.cassy.ui.pos.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.RestaurantMenu
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.azuredevid.cassy.R
import com.azuredevid.cassy.data.model.CatalogProduct
import com.azuredevid.cassy.ui.designsystem.CassyCard
import com.azuredevid.cassy.ui.designsystem.CassyDimens
import com.azuredevid.cassy.ui.designsystem.CassyEmptyState
import com.azuredevid.cassy.ui.designsystem.CassyErrorBanner
import com.azuredevid.cassy.ui.designsystem.CassyShimmerBox
import com.azuredevid.cassy.ui.designsystem.CassyStatusBadge
import com.azuredevid.cassy.ui.designsystem.CassyTextField
import com.azuredevid.cassy.ui.designsystem.CassyToastTone
import com.azuredevid.cassy.ui.theme.statusErrBg
import com.azuredevid.cassy.ui.theme.statusErrFg
import java.text.NumberFormat

/**
 * Panel 2: Product Catalog Grid in landscape POS with search, shimmer skeleton, and structured product cards.
 */
@Composable
fun PosCatalogGrid(
    gridWidth: Dp,
    displayedProducts: List<CatalogProduct>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    isLoadingCatalog: Boolean,
    catalogError: String? = null,
    onRetryCatalog: () -> Unit = {},
    onProductSelected: (CatalogProduct) -> Unit,
    rupiahFormat: NumberFormat,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clearSearchDesc = stringResource(R.string.a11y_clear_search)

    Column(
        modifier = modifier
            .width(gridWidth)
            .fillMaxHeight()
            .padding(horizontal = CassyDimens.SpaceMd, vertical = CassyDimens.SpaceSm),
        verticalArrangement = Arrangement.spacedBy(CassyDimens.SpaceSm)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.catalog_title, displayedProducts.size),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            CassyStatusBadge(
                text = stringResource(R.string.catalog_source_cdn),
                tone = CassyToastTone.INFO
            )
        }

        CassyTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            label = stringResource(R.string.search_placeholder),
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(
                        onClick = { onSearchQueryChange("") },
                        modifier = Modifier.defaultMinSize(
                            minWidth = CassyDimens.TouchTargetMin,
                            minHeight = CassyDimens.TouchTargetMin
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = clearSearchDesc
                        )
                    }
                }
            }
        )

        when {
            isLoadingCatalog -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 148.dp),
                    horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceMd),
                    verticalArrangement = Arrangement.spacedBy(CassyDimens.SpaceMd),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(6) {
                        CassyCard(contentPadding = CassyDimens.SpaceSm) {
                            CassyShimmerBox(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1.15f)
                            )
                            Spacer(Modifier.height(CassyDimens.SpaceSm))
                            CassyShimmerBox(
                                modifier = Modifier
                                    .fillMaxWidth(0.75f)
                                    .height(14.dp)
                            )
                            Spacer(Modifier.height(CassyDimens.SpaceXs))
                            CassyShimmerBox(
                                modifier = Modifier
                                    .fillMaxWidth(0.45f)
                                    .height(14.dp)
                            )
                        }
                    }
                }
            }

            !catalogError.isNullOrBlank() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CassyErrorBanner(
                        message = catalogError,
                        tone = CassyToastTone.ERROR,
                        onRetry = onRetryCatalog
                    )
                }
            }

            displayedProducts.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CassyEmptyState(
                        icon = Icons.Rounded.RestaurantMenu,
                        title = if (searchQuery.isNotBlank()) {
                            stringResource(R.string.catalog_empty_search, searchQuery)
                        } else {
                            stringResource(R.string.catalog_empty)
                        },
                        subtitle = stringResource(R.string.cart_empty_subtitle)
                    )
                }
            }

            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 148.dp),
                    horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceMd),
                    verticalArrangement = Arrangement.spacedBy(CassyDimens.SpaceMd),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(displayedProducts, key = { it.id }) { product ->
                        val available = product.isAvailable
                        CassyCard(
                            onClick = { onProductSelected(product) },
                            enabled = available,
                            contentPadding = 0.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .alpha(if (available) 1f else 0.55f)
                        ) {
                            val imageRequest = remember(context, product.imageUrl) {
                                ImageRequest.Builder(context)
                                    .data(product.imageUrl?.takeIf { it.isNotBlank() } ?: R.drawable.img_cassy_menu_default)
                                    .size(256)
                                    .precision(Precision.INEXACT)
                                    .placeholder(R.drawable.img_cassy_menu_default)
                                    .error(R.drawable.img_cassy_menu_default)
                                    .fallback(R.drawable.img_cassy_menu_default)
                                    .crossfade(true)
                                    .build()
                            }
                            Box {
                                AsyncImage(
                                    model = imageRequest,
                                    contentDescription = product.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1.15f)
                                        .clip(
                                            RoundedCornerShape(
                                                topStart = CassyDimens.RadiusCard,
                                                topEnd = CassyDimens.RadiusCard
                                            )
                                        )
                                )

                                Surface(
                                    shape = RoundedCornerShape(bottomEnd = CassyDimens.RadiusBadge),
                                    color = when (product.prepStation.lowercase()) {
                                        "bar" -> MaterialTheme.colorScheme.tertiaryContainer
                                        "kitchen", "hot_kitchen" -> MaterialTheme.colorScheme.secondaryContainer
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    modifier = Modifier.align(Alignment.TopStart)
                                ) {
                                    Text(
                                        text = product.prepStation.uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = when (product.prepStation.lowercase()) {
                                            "bar" -> MaterialTheme.colorScheme.onTertiaryContainer
                                            "kitchen", "hot_kitchen" -> MaterialTheme.colorScheme.onSecondaryContainer
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.padding(horizontal = CassyDimens.SpaceSm, vertical = 2.dp)
                                    )
                                }

                                if (!available) {
                                    Surface(
                                        shape = RoundedCornerShape(bottomStart = CassyDimens.RadiusBadge),
                                        color = statusErrBg(),
                                        modifier = Modifier.align(Alignment.TopEnd)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.catalog_sold_out_badge),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = statusErrFg(),
                                            modifier = Modifier.padding(horizontal = CassyDimens.SpaceSm, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            Column(modifier = Modifier.padding(CassyDimens.SpaceMd)) {
                                Text(
                                    text = product.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(CassyDimens.SpaceXxs))
                                Text(
                                    text = stringResource(
                                        R.string.cash_chip_rp,
                                        rupiahFormat.format(product.basePriceIdr)
                                    ),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(CassyDimens.SpaceXxs))
                                Text(
                                    text = stringResource(R.string.stock_label, product.stockQuantity),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (product.stockQuantity < 10) {
                                        statusErrFg()
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
