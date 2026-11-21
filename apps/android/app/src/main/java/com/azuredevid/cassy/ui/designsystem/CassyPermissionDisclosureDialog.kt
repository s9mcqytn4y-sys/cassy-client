package com.azuredevid.cassy.ui.designsystem

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.azuredevid.cassy.R

/**
 * Google Play Policy Prominent Disclosure dialog displayed before requesting a runtime permission
 * (such as CAMERA for QR pairing). Explains what data is accessed, why, and that it stays on device.
 */
@Composable
fun CassyPermissionDisclosureDialog(
    title: String = stringResource(R.string.permission_camera_disclosure_title),
    body: String = stringResource(R.string.permission_camera_disclosure_body),
    confirmText: String = stringResource(R.string.permission_camera_disclosure_continue),
    cancelText: String = stringResource(R.string.btn_cancel),
    icon: ImageVector = Icons.Rounded.PhotoCamera,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    CassyModalDialog(
        title = title,
        icon = icon,
        maxWidth = CassyDimens.ModalWidthNarrow,
        onDismissRequest = onDismiss,
        modifier = modifier,
        footer = {
            CassySecondaryButton(
                text = cancelText,
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            )
            CassyPrimaryButton(
                text = confirmText,
                onClick = onConfirm,
                modifier = Modifier.weight(1f)
            )
        }
    ) {
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
