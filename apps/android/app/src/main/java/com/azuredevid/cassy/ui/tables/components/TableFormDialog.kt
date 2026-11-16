package com.azuredevid.cassy.ui.tables.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.TableRestaurant
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.azuredevid.cassy.R
import com.azuredevid.cassy.domain.model.TableDraft
import com.azuredevid.cassy.domain.model.TableEntry
import com.azuredevid.cassy.ui.designsystem.CassyDimens
import com.azuredevid.cassy.ui.designsystem.CassyModalDialog
import com.azuredevid.cassy.ui.designsystem.CassyPrimaryButton
import com.azuredevid.cassy.ui.designsystem.CassySecondaryButton
import com.azuredevid.cassy.ui.designsystem.CassyTextField

private const val MIN_CAPACITY = 1
private const val MAX_CAPACITY = 100

/**
 * Create or edit form for one table. Field limits mirror the backend `TableInput`; the server stays
 * the authority and its 409/422 messages are shown by the screen. Input survives rotation.
 */
@Composable
fun TableFormDialog(
    initial: TableEntry?,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSave: (TableDraft) -> Unit
) {
    var number by rememberSaveable { mutableStateOf(initial?.tableNumber.orEmpty()) }
    var label by rememberSaveable { mutableStateOf(initial?.label.orEmpty()) }
    var section by rememberSaveable { mutableStateOf(initial?.section.orEmpty()) }
    var capacityText by rememberSaveable { mutableStateOf((initial?.capacity ?: 4).toString()) }
    var isActive by rememberSaveable { mutableStateOf(initial?.isActive ?: true) }

    val capacity = capacityText.toIntOrNull()
    val capacityValid = capacity != null && capacity in MIN_CAPACITY..MAX_CAPACITY
    val canSave = number.isNotBlank() && capacityValid && !isSubmitting

    CassyModalDialog(
        title = stringResource(if (initial == null) R.string.tables_add_title else R.string.tables_edit_title),
        icon = Icons.Rounded.TableRestaurant,
        maxWidth = CassyDimens.ModalWidthNarrow,
        dismissEnabled = !isSubmitting,
        onDismissRequest = onDismiss,
        footer = {
            CassySecondaryButton(
                text = stringResource(R.string.tables_btn_cancel),
                onClick = onDismiss,
                enabled = !isSubmitting
            )
            CassyPrimaryButton(
                text = stringResource(R.string.tables_btn_save),
                onClick = {
                    onSave(
                        TableDraft(
                            tableNumber = number,
                            label = label,
                            section = section,
                            capacity = capacity ?: MIN_CAPACITY,
                            isActive = isActive
                        )
                    )
                },
                enabled = canSave,
                loading = isSubmitting
            )
        }
    ) {
        CassyTextField(
            value = number,
            onValueChange = { number = it.take(50) },
            label = stringResource(R.string.tables_field_number),
            placeholder = stringResource(R.string.tables_field_number_hint),
            isError = number.isBlank()
        )
        CassyTextField(
            value = label,
            onValueChange = { label = it.take(100) },
            label = stringResource(R.string.tables_field_label),
            supportingText = stringResource(R.string.tables_field_label_hint)
        )
        CassyTextField(
            value = section,
            onValueChange = { section = it.take(50) },
            label = stringResource(R.string.tables_field_section),
            placeholder = stringResource(R.string.tables_field_section_hint)
        )
        CassyTextField(
            value = capacityText,
            onValueChange = { capacityText = it.filter(Char::isDigit).take(3) },
            label = stringResource(R.string.tables_field_capacity),
            supportingText = stringResource(R.string.tables_field_capacity_hint, MIN_CAPACITY, MAX_CAPACITY),
            isError = !capacityValid,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        if (initial != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = CassyDimens.TouchTargetMin),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.tables_field_active),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = isActive, onCheckedChange = { isActive = it }, enabled = !isSubmitting)
            }
        }
    }
}
