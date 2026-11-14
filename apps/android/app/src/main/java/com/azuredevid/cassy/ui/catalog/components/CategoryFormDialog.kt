package com.azuredevid.cassy.ui.catalog.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Category
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.azuredevid.cassy.R
import com.azuredevid.cassy.data.model.CatalogCategory
import com.azuredevid.cassy.ui.designsystem.CassyDimens
import com.azuredevid.cassy.ui.designsystem.CassyModalDialog
import com.azuredevid.cassy.ui.designsystem.CassyPrimaryButton
import com.azuredevid.cassy.ui.designsystem.CassySecondaryButton
import com.azuredevid.cassy.ui.designsystem.CassyTextField

@Composable
fun CategoryFormDialog(
    initial: CatalogCategory?,
    onDismiss: () -> Unit,
    onSave: (String, Int) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var orderText by remember { mutableStateOf((initial?.displayOrder ?: 0).toString()) }

    CassyModalDialog(
        title = stringResource(
            if (initial == null) R.string.catalog_add_category else R.string.catalog_edit_category
        ),
        icon = Icons.Rounded.Category,
        maxWidth = CassyDimens.ModalWidthNarrow,
        onDismissRequest = onDismiss,
        footer = {
            CassySecondaryButton(
                text = stringResource(R.string.catalog_btn_cancel),
                onClick = onDismiss
            )
            CassyPrimaryButton(
                text = stringResource(R.string.catalog_btn_save),
                onClick = { onSave(name, orderText.toIntOrNull() ?: 0) }
            )
        }
    ) {
        CassyTextField(
            value = name,
            onValueChange = { name = it },
            label = stringResource(R.string.catalog_field_category_name)
        )
        CassyTextField(
            value = orderText,
            onValueChange = { orderText = it.filter { ch -> ch.isDigit() } },
            label = stringResource(R.string.catalog_field_display_order),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
    }
}
