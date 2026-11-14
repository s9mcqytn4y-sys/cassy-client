package com.azuredevid.cassy.ui.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.azuredevid.cassy.ui.theme.statusErrBg
import com.azuredevid.cassy.ui.theme.statusErrFg

@Composable
fun CassyPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
    height: Dp = CassyDimens.ButtonHeight,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary
) {
    val interactionSource = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(CassyDimens.RadiusButton),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        contentPadding = PaddingValues(horizontal = CassyDimens.SpaceLg, vertical = CassyDimens.SpaceSm),
        modifier = modifier
            .defaultMinSize(minWidth = CassyDimens.TouchTargetMin, minHeight = height)
            .cassyPressScale(interactionSource, enabled && !loading)
    ) {
        CassyButtonContent(text = text, loading = loading, icon = icon, tint = contentColor)
    }
}

@Composable
fun CassySecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
    height: Dp = CassyDimens.ButtonHeight,
    contentColor: Color = MaterialTheme.colorScheme.primary,
    borderColor: Color = MaterialTheme.colorScheme.outline
) {
    val interactionSource = remember { MutableInteractionSource() }
    OutlinedButton(
        onClick = onClick,
        enabled = enabled && !loading,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(CassyDimens.RadiusButton),
        border = BorderStroke(1.dp, borderColor),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = contentColor),
        contentPadding = PaddingValues(horizontal = CassyDimens.SpaceLg, vertical = CassyDimens.SpaceSm),
        modifier = modifier
            .defaultMinSize(minWidth = CassyDimens.TouchTargetMin, minHeight = height)
            .cassyPressScale(interactionSource, enabled && !loading)
    ) {
        CassyButtonContent(text = text, loading = loading, icon = icon, tint = contentColor)
    }
}

@Composable
fun CassyTonalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
    height: Dp = CassyDimens.ButtonHeight,
    colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface
    )
) {
    val interactionSource = remember { MutableInteractionSource() }
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled && !loading,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(CassyDimens.RadiusButton),
        colors = colors,
        contentPadding = PaddingValues(horizontal = CassyDimens.SpaceLg, vertical = CassyDimens.SpaceSm),
        modifier = modifier
            .defaultMinSize(minWidth = CassyDimens.TouchTargetMin, minHeight = height)
            .cassyPressScale(interactionSource, enabled && !loading)
    ) {
        CassyButtonContent(text = text, loading = loading, icon = icon, tint = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun CassyDangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
    height: Dp = CassyDimens.ButtonHeight
) {
    val interactionSource = remember { MutableInteractionSource() }
    val bg = statusErrBg()
    val fg = statusErrFg()
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(CassyDimens.RadiusButton),
        colors = ButtonDefaults.buttonColors(containerColor = bg, contentColor = fg),
        border = BorderStroke(1.dp, fg.copy(alpha = 0.25f)),
        contentPadding = PaddingValues(horizontal = CassyDimens.SpaceLg, vertical = CassyDimens.SpaceSm),
        modifier = modifier
            .defaultMinSize(minWidth = CassyDimens.TouchTargetMin, minHeight = height)
            .cassyPressScale(interactionSource, enabled && !loading)
    ) {
        CassyButtonContent(text = text, loading = loading, icon = icon, tint = fg)
    }
}

@Composable
fun CassyTextActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    contentColor: Color = MaterialTheme.colorScheme.primary
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.textButtonColors(contentColor = contentColor),
        modifier = modifier.defaultMinSize(minWidth = CassyDimens.TouchTargetMin, minHeight = CassyDimens.TouchTargetMin)
    ) {
        CassyButtonContent(text = text, loading = false, icon = icon, tint = contentColor)
    }
}

@Composable
private fun CassyButtonContent(
    text: String,
    loading: Boolean,
    icon: ImageVector?,
    tint: Color
) {
    if (loading) {
        CircularProgressIndicator(
            modifier = Modifier.size(CassyDimens.IconSizeSm),
            strokeWidth = 2.dp,
            color = tint
        )
        Spacer(Modifier.width(CassyDimens.SpaceSm))
    } else if (icon != null) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(CassyDimens.IconSizeSm)
        )
        Spacer(Modifier.width(CassyDimens.SpaceSm))
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}
