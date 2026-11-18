package com.azuredevid.cassy.ui.shift.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.azuredevid.cassy.R
import com.azuredevid.cassy.core.util.ThousandsSeparatorTransformation
import com.azuredevid.cassy.domain.model.CashDrawer
import com.azuredevid.cassy.ui.designsystem.CassyCard
import com.azuredevid.cassy.ui.designsystem.CassyDimens
import com.azuredevid.cassy.ui.designsystem.CassyErrorBanner
import com.azuredevid.cassy.ui.designsystem.CassyModalDialog
import com.azuredevid.cassy.ui.designsystem.CassyPrimaryButton
import com.azuredevid.cassy.ui.designsystem.CassySecondaryButton
import com.azuredevid.cassy.ui.designsystem.CassyTextField
import com.azuredevid.cassy.ui.designsystem.CassyToastTone
import java.text.NumberFormat
import java.util.Locale

@Composable
fun OpenShiftModal(
    drawers: List<CashDrawer> = emptyList(),
    selectedDrawerId: String? = null,
    onDrawerSelected: (String) -> Unit = {},
    openingFloatInput: String,
    notes: String,
    isOpening: Boolean,
    errorMessage: String? = null,
    onOpeningFloatChanged: (String) -> Unit,
    onNotesChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rupiahFormatter = remember { NumberFormat.getIntegerInstance(Locale("id", "ID")) }
    val currentAmount = openingFloatInput.toLongOrNull() ?: 0L
    val presets = remember { listOf(100_000L, 200_000L, 500_000L, 1_000_000L) }

    CassyModalDialog(
        title = stringResource(R.string.shift_open_title),
        subtitle = stringResource(R.string.shift_open_subtitle),
        icon = Icons.Rounded.LockOpen,
        maxWidth = CassyDimens.ModalWidthStandard,
        dismissEnabled = !isOpening,
        onDismissRequest = onDismiss,
        modifier = modifier,
        footer = {
            CassySecondaryButton(
                text = stringResource(R.string.btn_cancel),
                onClick = onDismiss,
                enabled = !isOpening,
                modifier = Modifier.weight(1f)
            )
            CassyPrimaryButton(
                text = stringResource(R.string.shift_btn_open),
                onClick = onSubmit,
                loading = isOpening,
                modifier = Modifier.weight(1f)
            )
        }
    ) {
        if (drawers.isNotEmpty()) {
            Text(
                text = stringResource(R.string.shift_drawer_select_label),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceSm)
            ) {
                drawers.forEach { drawer ->
                    val isSelected = drawer.id == selectedDrawerId
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = CassyDimens.TouchTargetMin)
                            .selectable(
                                selected = isSelected,
                                role = Role.RadioButton,
                                onClick = { onDrawerSelected(drawer.id) }
                            ),
                        shape = RoundedCornerShape(CassyDimens.RadiusButton),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline
                            }
                        )
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = CassyDimens.SpaceMd),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = drawer.name,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                }
            }
        }

        CassyCard(
            containerColor = MaterialTheme.colorScheme.background,
            contentPadding = CassyDimens.SpaceLg
        ) {
            Text(
                text = stringResource(R.string.shift_open_float_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.cash_chip_rp, rupiahFormatter.format(currentAmount)),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceSm)
        ) {
            presets.forEach { preset ->
                val isSelected = currentAmount == preset
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = CassyDimens.TouchTargetMin)
                        .selectable(
                            selected = isSelected,
                            role = Role.RadioButton,
                            onClick = { onOpeningFloatChanged(preset.toString()) }
                        ),
                    shape = RoundedCornerShape(CassyDimens.RadiusButton),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.shift_preset_rb, (preset / 1000L).toInt()),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }
            }
        }

        CassyTextField(
            value = openingFloatInput,
            onValueChange = { onOpeningFloatChanged(it.filter(Char::isDigit).take(12)) },
            label = stringResource(R.string.shift_open_custom_amount),
            placeholder = "0",
            visualTransformation = ThousandsSeparatorTransformation,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        CassyTextField(
            value = notes,
            onValueChange = onNotesChanged,
            label = stringResource(R.string.shift_notes_label),
            placeholder = stringResource(R.string.shift_open_notes_placeholder)
        )

        if (!errorMessage.isNullOrBlank()) {
            CassyErrorBanner(message = errorMessage, tone = CassyToastTone.ERROR)
        }
    }
}
