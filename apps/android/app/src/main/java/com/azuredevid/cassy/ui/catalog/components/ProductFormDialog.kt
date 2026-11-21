package com.azuredevid.cassy.ui.catalog.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Fastfood
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.azuredevid.cassy.R
import com.azuredevid.cassy.data.model.CatalogCategory
import com.azuredevid.cassy.data.model.CatalogProduct
import com.azuredevid.cassy.data.model.ProductUpsertInput
import com.azuredevid.cassy.ui.designsystem.CassyDimens
import com.azuredevid.cassy.ui.designsystem.CassyModalDialog
import com.azuredevid.cassy.ui.designsystem.CassyPrimaryButton
import com.azuredevid.cassy.ui.designsystem.CassySecondaryButton
import com.azuredevid.cassy.ui.designsystem.CassyTextField

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProductFormDialog(
    initial: CatalogProduct?,
    categories: List<CatalogCategory>,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSave: (ProductUpsertInput) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var description by remember { mutableStateOf(initial?.description.orEmpty()) }
    var selectedCategoryId by remember { mutableStateOf(initial?.categoryId.orEmpty()) }
    var productType by remember { mutableStateOf(initial?.productType ?: "prepared") }
    var prepStation by remember { mutableStateOf(initial?.prepStation ?: "kitchen") }
    var priceText by remember { mutableStateOf(initial?.basePriceIdr?.toString().orEmpty()) }

    val stationOptions = listOf(
        "bar" to R.string.catalog_station_bar,
        "kitchen" to R.string.catalog_station_kitchen,
        "none" to R.string.catalog_station_none
    )
    val typeOptions = listOf(
        "prepared" to R.string.catalog_type_prepared,
        "ready_to_serve" to R.string.catalog_type_ready
    )

    CassyModalDialog(
        title = stringResource(
            if (initial == null) R.string.catalog_add_product else R.string.catalog_edit_product
        ),
        icon = Icons.Rounded.Fastfood,
        maxWidth = CassyDimens.ModalWidthStandard,
        dismissEnabled = !isSubmitting,
        onDismissRequest = onDismiss,
        footer = {
            CassySecondaryButton(
                text = stringResource(R.string.catalog_btn_cancel),
                onClick = onDismiss,
                enabled = !isSubmitting
            )
            CassyPrimaryButton(
                text = stringResource(R.string.catalog_btn_save),
                loading = isSubmitting,
                onClick = {
                    onSave(
                        ProductUpsertInput(
                            categoryId = selectedCategoryId.ifBlank { null },
                            clearCategory = initial != null && selectedCategoryId.isBlank(),
                            name = name,
                            description = description,
                            productType = productType,
                            prepStation = prepStation,
                            isAvailable = initial?.isAvailable ?: true,
                            basePriceIdr = if (initial == null) priceText.toLongOrNull() else null
                        )
                    )
                }
            )
        }
    ) {
        CassyTextField(
            value = name,
            onValueChange = { name = it },
            label = stringResource(R.string.catalog_field_product_name)
        )
        CassyTextField(
            value = description,
            onValueChange = { description = it },
            label = stringResource(R.string.catalog_field_description),
            singleLine = false,
            maxLines = 2
        )
        if (initial == null) {
            CassyTextField(
                value = priceText,
                onValueChange = { priceText = it.filter { ch -> ch.isDigit() } },
                label = stringResource(R.string.catalog_field_base_price),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }
        Text(
            text = stringResource(R.string.catalog_field_station),
            style = MaterialTheme.typography.labelLarge
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceSm),
            verticalArrangement = Arrangement.spacedBy(CassyDimens.SpaceSm)
        ) {
            stationOptions.forEach { (key, labelRes) ->
                FilterChip(
                    selected = prepStation == key,
                    onClick = { prepStation = key },
                    label = { Text(stringResource(labelRes)) },
                    modifier = Modifier.defaultMinSize(minHeight = CassyDimens.TouchTargetMin)
                )
            }
        }
        Text(
            text = stringResource(R.string.catalog_field_type),
            style = MaterialTheme.typography.labelLarge
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceSm),
            verticalArrangement = Arrangement.spacedBy(CassyDimens.SpaceSm)
        ) {
            typeOptions.forEach { (key, labelRes) ->
                FilterChip(
                    selected = productType == key,
                    onClick = { productType = key },
                    label = { Text(stringResource(labelRes)) },
                    modifier = Modifier.defaultMinSize(minHeight = CassyDimens.TouchTargetMin)
                )
            }
        }
        if (categories.isNotEmpty()) {
            Text(
                text = stringResource(R.string.catalog_field_category),
                style = MaterialTheme.typography.labelLarge
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceSm),
                verticalArrangement = Arrangement.spacedBy(CassyDimens.SpaceSm)
            ) {
                categories.forEach { cat ->
                    val isSelected = selectedCategoryId == cat.id
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedCategoryId = if (isSelected) "" else cat.id
                        },
                        leadingIcon = if (isSelected) {
                            { Icon(Icons.Rounded.Check, contentDescription = null) }
                        } else null,
                        label = { Text(cat.name) },
                        modifier = Modifier.defaultMinSize(minHeight = CassyDimens.TouchTargetMin)
                    )
                }
            }
        }
    }
}
