package com.azuredevid.cassy.ui.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Standard bordered surface card used across Cassy POS screens.
 */
@Composable
fun CassyCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = MaterialTheme.colorScheme.outline,
    shape: Shape = RoundedCornerShape(CassyDimens.RadiusCard),
    elevation: Dp = CassyDimens.ElevationSubtle,
    contentPadding: Dp = CassyDimens.SpaceLg,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    if (onClick != null) {
        val interactionSource = remember { MutableInteractionSource() }
        Surface(
            onClick = onClick,
            enabled = enabled,
            interactionSource = interactionSource,
            shape = shape,
            color = containerColor,
            shadowElevation = elevation,
            border = BorderStroke(1.dp, borderColor),
            modifier = modifier.cassyPressScale(interactionSource, enabled)
        ) {
            Column(modifier = Modifier.padding(contentPadding), content = content)
        }
    } else {
        Surface(
            shape = shape,
            color = containerColor,
            shadowElevation = elevation,
            border = BorderStroke(1.dp, borderColor),
            modifier = modifier
        ) {
            Column(modifier = Modifier.padding(contentPadding), content = content)
        }
    }
}

@Composable
fun CassySectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(CassyDimens.SpaceXs))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (trailing != null) {
            trailing()
        }
    }
}

/**
 * Tablet-first two-pane layout that eliminates dead landscape whitespace on 2560x1600 tablets
 * while collapsing gracefully into a scrollable single column on narrower viewports.
 */
@Composable
fun CassySplitPane(
    modifier: Modifier = Modifier,
    leftWeight: Float = 0.44f,
    rightWeight: Float = 0.56f,
    gap: Dp = CassyDimens.SpaceXl,
    leftContent: @Composable () -> Unit,
    rightContent: @Composable () -> Unit
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        if (maxWidth >= CassyDimens.SplitPaneBreakpoint) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(CassyDimens.SpaceXl),
                horizontalArrangement = Arrangement.spacedBy(gap)
            ) {
                Column(
                    modifier = Modifier
                        .weight(leftWeight)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.Top
                ) {
                    leftContent()
                }
                Column(
                    modifier = Modifier
                        .weight(rightWeight)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.Top
                ) {
                    rightContent()
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(CassyDimens.SpaceLg),
                verticalArrangement = Arrangement.spacedBy(CassyDimens.SpaceLg)
            ) {
                leftContent()
                rightContent()
            }
        }
    }
}
