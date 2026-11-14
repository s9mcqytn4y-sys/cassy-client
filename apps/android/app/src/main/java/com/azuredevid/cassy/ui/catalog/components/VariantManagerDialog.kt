package com.azuredevid.cassy.ui.catalog.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import com.azuredevid.cassy.R
import com.azuredevid.cassy.data.model.CatalogProduct
import com.azuredevid.cassy.data.model.CatalogVariant
import com.azuredevid.cassy.data.model.VariantUpsertInput
import com.azuredevid.cassy.ui.catalog.CatalogManagementUiEffect
import com.azuredevid.cassy.ui.designsystem.CassyDimens
import com.azuredevid.cassy.ui.designsystem.CassyModalDialog
import com.azuredevid.cassy.ui.designsystem.CassyPrimaryButton
import com.azuredevid.cassy.ui.designsystem.CassySecondaryButton
import com.azuredevid.cassy.ui.designsystem.CassyTextField
import kotlinx.coroutines.flow.SharedFlow
import java.text.NumberFormat

@Composable
fun VariantManagerDialog(
    product: CatalogProduct,
    rupiahFormat: NumberFormat,
    isSubmitting: Boolean,
    effects: SharedFlow<CatalogManagementUiEffect>,
    onDismiss: () -> Unit,
    onSaveVariant: (String?, VariantUpsertInput) -> Unit,
    onArchiveVariant: (String) -> Unit
) {
    var editingVariant by remember { mutableStateOf<CatalogVariant?>(null) }
    var name by remember { mutableStateOf("") }
    var priceText by remember { mutableStateOf("") }
    var isDefault by remember { mutableStateOf(false) }

    LaunchedEffect(effects) {
        effects.collect { effect ->
            if (effect is CatalogManagementUiEffect.VariantSaved) {
                editingVariant = null
                name = ""
                priceText = ""
                isDefault = false
            }
        }
    }

    CassyModalDialog(
        title = "${stringResource(R.string.catalog_btn_variants)}: ${product.name}",
        icon = Icons.Rounded.Tune,
        maxWidth = CassyDimens.ModalWidthStandard,
        dismissEnabled = !isSubmitting,
        onDismissRequest = onDismiss,
        footer = {
            CassySecondaryButton(
                text = stringResource(R.string.catalog_btn_cancel),
                onClick = onDismiss,
                enabled = !isSubmitting
            )
        }
    ) {
        product.variants.forEach { variant ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (variant.isDefault) {
                            stringResource(R.string.catalog_variant_default, variant.name)
                        } else {
                            variant.name
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Rp ${rupiahFormat.format(variant.price)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row {
                    IconButton(
                        onClick = {
                            editingVariant = variant
                            name = variant.name
                            priceText = variant.price.toString()
                            isDefault = variant.isDefault
                        },
                        modifier = Modifier.defaultMinSize(
                            minWidth = CassyDimens.TouchTargetMin,
                            minHeight = CassyDimens.TouchTargetMin
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = stringResource(R.string.catalog_variant_edit)
                        )
                    }
                    if (!variant.isDefault) {
                        IconButton(
                            onClick = { onArchiveVariant(variant.id) },
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
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        }

        CassyTextField(
            value = name,
            onValueChange = { name = it },
            label = stringResource(R.string.catalog_variant_name_label)
        )
        CassyTextField(
            value = priceText,
            onValueChange = { priceText = it.filter { ch -> ch.isDigit() } },
            label = stringResource(R.string.catalog_variant_price_label),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        CassyPrimaryButton(
            text = stringResource(
                if (editingVariant == null) {
                    R.string.catalog_variant_btn_add
                } else {
                    R.string.catalog_variant_btn_update
                }
            ),
            loading = isSubmitting,
            onClick = {
                val price = priceText.toLongOrNull() ?: return@CassyPrimaryButton
                onSaveVariant(
                    editingVariant?.id,
                    VariantUpsertInput(name = name, price = price, isDefault = isDefault)
                )
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}
