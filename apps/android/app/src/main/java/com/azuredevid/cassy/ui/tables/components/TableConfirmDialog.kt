package com.azuredevid.cassy.ui.tables.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.azuredevid.cassy.ui.designsystem.CassyDangerButton
import com.azuredevid.cassy.ui.designsystem.CassyDimens
import com.azuredevid.cassy.ui.designsystem.CassyModalDialog
import com.azuredevid.cassy.ui.designsystem.CassySecondaryButton

/**
 * Confirmation for destructive table actions (QR rotation, archive). The confirm button uses the
 * danger style because both actions break printed stickers immediately.
 */
@Composable
fun TableConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    cancelLabel: String,
    icon: ImageVector,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    CassyModalDialog(
        title = title,
        icon = icon,
        iconContainerColor = MaterialTheme.colorScheme.errorContainer,
        iconTint = MaterialTheme.colorScheme.error,
        maxWidth = CassyDimens.ModalWidthNarrow,
        onDismissRequest = onDismiss,
        footer = {
            CassySecondaryButton(text = cancelLabel, onClick = onDismiss)
            CassyDangerButton(text = confirmLabel, onClick = onConfirm)
        }
    ) {
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
