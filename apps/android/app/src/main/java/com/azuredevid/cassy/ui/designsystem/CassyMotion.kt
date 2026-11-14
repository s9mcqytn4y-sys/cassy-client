package com.azuredevid.cassy.ui.designsystem

import android.provider.Settings
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext

private const val DURATION_FAST_MS = 180
private const val PRESS_SCALE_TARGET = 0.98f

/**
 * Reads system animator duration scale so tactile motion degrades cleanly when disabled.
 */
@Composable
fun rememberReducedMotionEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        val scale = runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
        }.getOrDefault(1f)
        scale == 0f
    }
}

fun <T> cassySpringSpec() = spring<T>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMedium
)

fun cassyFastContentTransform(reducedMotion: Boolean): ContentTransform =
    if (reducedMotion) {
        fadeIn(tween(0)) togetherWith fadeOut(tween(0))
    } else {
        fadeIn(tween(DURATION_FAST_MS, easing = FastOutSlowInEasing)) togetherWith
            fadeOut(tween(DURATION_FAST_MS / 2, easing = FastOutSlowInEasing))
    }

/**
 * Applies crisp cashier tactile press feedback while honoring reduced-motion settings.
 */
fun Modifier.cassyPressScale(
    interactionSource: InteractionSource,
    enabled: Boolean = true
): Modifier = composed {
    val reducedMotion = rememberReducedMotionEnabled()
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (enabled && isPressed && !reducedMotion) PRESS_SCALE_TARGET else 1f,
        animationSpec = cassySpringSpec(),
        label = "cassyPressScale"
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Loading placeholder block that stops pulsing when reduced motion is enabled.
 */
@Composable
fun CassyShimmerBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(CassyDimens.RadiusCard)
) {
    val reducedMotion = rememberReducedMotionEnabled()
    val alpha = if (reducedMotion) {
        0.55f
    } else {
        val transition = rememberInfiniteTransition(label = "cassyShimmer")
        val animated by transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 0.75f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 850, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "cassyShimmerAlpha"
        )
        animated
    }
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
    )
}
