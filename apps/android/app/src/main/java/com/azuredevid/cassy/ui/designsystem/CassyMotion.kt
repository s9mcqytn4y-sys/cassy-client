package com.azuredevid.cassy.ui.designsystem

import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Animatable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView


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
        fadeIn(tween(CassyMotionTokens.FEEDBACK_MS, easing = FastOutSlowInEasing)) togetherWith
            fadeOut(tween(CassyMotionTokens.FEEDBACK_MS / 2, easing = FastOutSlowInEasing))
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
        targetValue = if (enabled && isPressed && !reducedMotion) CassyMotionTokens.PRESS_SCALE else 1f,
        animationSpec = cassySpringSpec(),
        label = "cassyPressScale"
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Brief scale pop each time [key] changes after the first composition (cart count, sent badge).
 */
fun Modifier.cassyPopOnChange(key: Any): Modifier = composed {
    val reducedMotion = rememberReducedMotionEnabled()
    val scale = remember { Animatable(1f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(key) {
        if (first) {
            first = false
            return@LaunchedEffect
        }
        if (reducedMotion) return@LaunchedEffect
        scale.snapTo(1.08f)
        scale.animateTo(1f, tween(CassyMotionTokens.RESULT_MS, easing = FastOutSlowInEasing))
    }
    graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

/**
 * Short horizontal shake plus a reject haptic when [errorKey] changes to a non-null value
 * (wrong PIN, rejected input). Reduced motion keeps the haptic and skips the shake.
 */
fun Modifier.cassyShakeOnError(errorKey: Any?): Modifier = composed {
    val reducedMotion = rememberReducedMotionEnabled()
    val view = LocalView.current
    val offset = remember { Animatable(0f) }
    LaunchedEffect(errorKey) {
        if (errorKey == null) return@LaunchedEffect
        runCatching { view.performHapticFeedback(HapticFeedbackConstants.REJECT) }
        if (reducedMotion) return@LaunchedEffect
        val step = CassyMotionTokens.RESULT_MS / 6
        for (x in listOf(-8f, 8f, -6f, 6f, -3f, 0f)) offset.animateTo(x, tween(step))
    }
    graphicsLayer { translationX = offset.value * density }
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
