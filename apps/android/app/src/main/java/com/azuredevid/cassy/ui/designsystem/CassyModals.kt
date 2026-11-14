package com.azuredevid.cassy.ui.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.azuredevid.cassy.R

/**
 * Consistent tablet modal container with >=48dp close target, scrollable body, and footer actions.
 */
@Composable
fun CassyModalDialog(
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconContainerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    maxWidth: Dp = CassyDimens.ModalWidthStandard,
    dismissEnabled: Boolean = true,
    scrollableBody: Boolean = true,
    footer: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = {
            if (dismissEnabled) onDismissRequest()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = dismissEnabled,
            dismissOnClickOutside = dismissEnabled
        )
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = maxWidth),
            shape = RoundedCornerShape(CassyDimens.RadiusModal),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = CassyDimens.ElevationModal,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(CassyDimens.SpaceXl)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceMd),
                    verticalAlignment = Alignment.Top
                ) {
                    if (icon != null) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(iconContainerColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = iconTint,
                                modifier = Modifier.size(CassyDimens.IconSizeLg)
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (!subtitle.isNullOrBlank()) {
                            Spacer(Modifier.height(CassyDimens.SpaceXs))
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismissRequest,
                        enabled = dismissEnabled,
                        modifier = Modifier.defaultMinSize(
                            minWidth = CassyDimens.TouchTargetMin,
                            minHeight = CassyDimens.TouchTargetMin
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.a11y_close_dialog),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(CassyDimens.SpaceLg))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
                Spacer(Modifier.height(CassyDimens.SpaceLg))

                val bodyModifier = if (scrollableBody) {
                    Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                } else {
                    Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                }

                Column(
                    modifier = bodyModifier,
                    verticalArrangement = Arrangement.spacedBy(CassyDimens.SpaceMd),
                    content = content
                )

                if (footer != null) {
                    Spacer(Modifier.height(CassyDimens.SpaceXl))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
                    Spacer(Modifier.height(CassyDimens.SpaceLg))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(
                            CassyDimens.SpaceMd,
                            Alignment.End
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                        content = footer
                    )
                }
            }
        }
    }
}
