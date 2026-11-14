package com.azuredevid.cassy.ui.catalog.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.azuredevid.cassy.R
import com.azuredevid.cassy.data.model.CatalogCategory
import com.azuredevid.cassy.ui.designsystem.CassyDimens

@Composable
fun CategorySidebarPanel(
    categories: List<CatalogCategory>,
    selectedCategoryId: String,
    canManageCatalog: Boolean,
    onSelectCategory: (String) -> Unit,
    onAddCategory: () -> Unit,
    onEditCategory: (CatalogCategory) -> Unit,
    onDeleteCategory: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(272.dp)
            .fillMaxHeight()
            .padding(CassyDimens.SpaceMd),
        verticalArrangement = Arrangement.spacedBy(CassyDimens.SpaceSm)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.catalog_categories_header),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (canManageCatalog) {
                IconButton(
                    onClick = onAddCategory,
                    modifier = Modifier.defaultMinSize(
                        minWidth = CassyDimens.TouchTargetMin,
                        minHeight = CassyDimens.TouchTargetMin
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.catalog_add_category)
                    )
                }
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(CassyDimens.SpaceSm)) {
            item(key = "all") {
                CategoryFilterItem(
                    name = stringResource(R.string.category_all),
                    selected = selectedCategoryId == "all",
                    canManage = false,
                    onSelect = { onSelectCategory("all") },
                    onEdit = {},
                    onDelete = {}
                )
            }
            items(categories, key = { it.id }) { category ->
                CategoryFilterItem(
                    name = category.name,
                    selected = selectedCategoryId == category.id,
                    canManage = canManageCatalog,
                    onSelect = { onSelectCategory(category.id) },
                    onEdit = { onEditCategory(category) },
                    onDelete = { onDeleteCategory(category.id) }
                )
            }
        }
    }
}

@Composable
private fun CategoryFilterItem(
    name: String,
    selected: Boolean,
    canManage: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(CassyDimens.RadiusButton),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        ),
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = CassyDimens.TouchTargetMin)
            .clickable(onClick = onSelect)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CassyDimens.SpaceMd, vertical = CassyDimens.SpaceXs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.weight(1f)
            )
            if (canManage) {
                Row {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.defaultMinSize(
                            minWidth = CassyDimens.TouchTargetMin,
                            minHeight = CassyDimens.TouchTargetMin
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = stringResource(R.string.catalog_edit_category)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.defaultMinSize(
                            minWidth = CassyDimens.TouchTargetMin,
                            minHeight = CassyDimens.TouchTargetMin
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = stringResource(R.string.catalog_btn_archive)
                        )
                    }
                }
            }
        }
    }
}
