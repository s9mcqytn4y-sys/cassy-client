package com.azuredevid.cassy.ui.pairing.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.NoPhotography
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.azuredevid.cassy.R
import com.azuredevid.cassy.ui.designsystem.CassyDimens
import com.azuredevid.cassy.ui.designsystem.CassyModalDialog
import com.azuredevid.cassy.ui.designsystem.CassyPrimaryButton
import com.azuredevid.cassy.ui.designsystem.CassySecondaryButton
import com.azuredevid.cassy.ui.theme.CassyPandan

/**
 * Modal recovery prompt displayed when camera permission is denied so the pairing form layout stays stable.
 */
@Composable
fun CameraPermissionDeniedDialog(
    onOpenAppSettings: () -> Unit,
    onUseHidScanner: () -> Unit,
    onDismiss: () -> Unit
) {
    CassyModalDialog(
        title = stringResource(R.string.pairing_camera_permission_denied_title),
        icon = Icons.Rounded.NoPhotography,
        iconContainerColor = Color(0xFFFEE2E2),
        iconTint = Color(0xFFB91C1C),
        onDismissRequest = onDismiss,
        footer = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = CassyPandan),
                modifier = Modifier.defaultMinSize(minHeight = CassyDimens.TouchTargetMin)
            ) {
                Text(
                    text = stringResource(R.string.pairing_camera_permission_cancel),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
            CassySecondaryButton(
                text = stringResource(R.string.pairing_btn_hid_scanner),
                onClick = onUseHidScanner,
                icon = Icons.Rounded.Keyboard
            )
            CassyPrimaryButton(
                text = stringResource(R.string.pairing_btn_open_app_settings),
                onClick = onOpenAppSettings,
                icon = Icons.Rounded.Settings
            )
        }
    ) {
        Spacer(Modifier.height(CassyDimens.SpaceXxs))
        Text(
            text = stringResource(R.string.pairing_camera_permission_denied_desc),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
