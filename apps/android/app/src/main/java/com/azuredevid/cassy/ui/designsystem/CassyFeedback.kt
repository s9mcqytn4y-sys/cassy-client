package com.azuredevid.cassy.ui.designsystem

import android.media.AudioManager
import android.media.ToneGenerator
import android.view.HapticFeedbackConstants
import android.view.SoundEffectConstants
import android.view.View
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarVisuals
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.azuredevid.cassy.R
import com.azuredevid.cassy.ui.theme.statusErrBg
import com.azuredevid.cassy.ui.theme.statusErrFg
import com.azuredevid.cassy.ui.theme.statusInfoBg
import com.azuredevid.cassy.ui.theme.statusInfoFg
import com.azuredevid.cassy.ui.theme.statusOkBg
import com.azuredevid.cassy.ui.theme.statusOkFg
import com.azuredevid.cassy.ui.theme.statusWarnBg
import com.azuredevid.cassy.ui.theme.statusWarnFg

enum class CassyToastTone {
    INFO,
    SUCCESS,
    WARNING,
    ERROR
}

data class CassySnackbarVisuals(
    override val message: String,
    val tone: CassyToastTone = CassyToastTone.INFO,
    override val actionLabel: String? = null,
    override val withDismissAction: Boolean = true,
    override val duration: SnackbarDuration = SnackbarDuration.Short
) : SnackbarVisuals

suspend fun SnackbarHostState.showCassySnackbar(
    message: String,
    tone: CassyToastTone = CassyToastTone.INFO,
    actionLabel: String? = null,
    duration: SnackbarDuration = SnackbarDuration.Short
): SnackbarResult {
    currentSnackbarData?.dismiss()
    return showSnackbar(
        CassySnackbarVisuals(
            message = message,
            tone = tone,
            actionLabel = actionLabel,
            withDismissAction = true,
            duration = duration
        )
    )
}

@Composable
private fun toneColors(tone: CassyToastTone): Pair<Color, Color> = when (tone) {
    CassyToastTone.SUCCESS -> statusOkBg() to statusOkFg()
    CassyToastTone.WARNING -> statusWarnBg() to statusWarnFg()
    CassyToastTone.ERROR -> statusErrBg() to statusErrFg()
    CassyToastTone.INFO -> statusInfoBg() to statusInfoFg()
}

private fun toneIcon(tone: CassyToastTone): ImageVector = when (tone) {
    CassyToastTone.SUCCESS -> Icons.Filled.CheckCircle
    CassyToastTone.WARNING -> Icons.Filled.Warning
    CassyToastTone.ERROR -> Icons.Filled.Error
    CassyToastTone.INFO -> Icons.Filled.Info
}

/**
 * Semantic Material 3 SnackbarHost styled to Cassy status tokens and Material Symbols.
 */
@Composable
fun CassySnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    SnackbarHost(
        hostState = hostState,
        modifier = modifier
    ) { snackbarData ->
        val visuals = snackbarData.visuals
        val tone = (visuals as? CassySnackbarVisuals)?.tone ?: CassyToastTone.INFO
        val (bg, fg) = toneColors(tone)
        val icon = toneIcon(tone)

        Surface(
            modifier = Modifier
                .padding(CassyDimens.SpaceLg)
                .widthIn(min = 300.dp, max = 560.dp),
            shape = RoundedCornerShape(CassyDimens.RadiusCard),
            color = bg,
            border = BorderStroke(1.dp, fg.copy(alpha = 0.3f)),
            shadowElevation = CassyDimens.ElevationModal
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = CassyDimens.SpaceLg,
                    vertical = CassyDimens.SpaceMd
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceMd)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = fg,
                    modifier = Modifier.size(CassyDimens.IconSizeMd)
                )
                Text(
                    text = visuals.message,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = fg,
                    modifier = Modifier.weight(1f)
                )
                visuals.actionLabel?.let { label ->
                    CassyTextActionButton(
                        text = label,
                        onClick = { snackbarData.performAction() },
                        contentColor = fg
                    )
                }
                if (visuals.withDismissAction) {
                    IconButton(
                        onClick = { snackbarData.dismiss() },
                        modifier = Modifier.defaultMinSize(
                            minWidth = CassyDimens.TouchTargetMin,
                            minHeight = CassyDimens.TouchTargetMin
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.a11y_close_dialog),
                            tint = fg
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CassyStatusBadge(
    text: String,
    tone: CassyToastTone,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    val (bg, fg) = toneColors(tone)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(CassyDimens.RadiusBadge),
        color = bg,
        border = BorderStroke(1.dp, fg.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = CassyDimens.SpaceMd, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = fg,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = fg
            )
        }
    }
}

@Composable
fun CassyErrorBanner(
    message: String,
    modifier: Modifier = Modifier,
    tone: CassyToastTone = CassyToastTone.ERROR,
    onRetry: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null
) {
    val (bg, fg) = toneColors(tone)
    val icon = toneIcon(tone)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CassyDimens.RadiusCard),
        color = bg,
        border = BorderStroke(1.dp, fg.copy(alpha = 0.28f))
    ) {
        Row(
            modifier = Modifier.padding(CassyDimens.SpaceMd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceMd)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(CassyDimens.IconSizeMd)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = fg,
                modifier = Modifier.weight(1f)
            )
            if (onRetry != null) {
                CassyTextActionButton(
                    text = stringResource(R.string.btn_retry),
                    onClick = onRetry,
                    contentColor = fg
                )
            }
            if (onDismiss != null) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.defaultMinSize(
                        minWidth = CassyDimens.TouchTargetMin,
                        minHeight = CassyDimens.TouchTargetMin
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.a11y_close_dialog),
                        tint = fg
                    )
                }
            }
        }
    }
}

@Composable
fun CassyEmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier.padding(CassyDimens.SpaceXl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(Modifier.height(CassyDimens.SpaceLg))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(CassyDimens.SpaceXs))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 360.dp)
        )
        if (!actionLabel.isNullOrBlank() && onAction != null) {
            Spacer(Modifier.height(CassyDimens.SpaceLg))
            CassyPrimaryButton(
                text = actionLabel,
                onClick = onAction
            )
        }
    }
}

/**
 * Proportional tactile (haptic) and system audio cues for cashier actions.
 * Respects device touch/sound preferences and never crashes on headless CI emulators.
 */
object CassyFeedbackCue {
    fun tap(view: View) {
        runCatching {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            view.playSoundEffect(SoundEffectConstants.CLICK)
        }
    }

    fun success(view: View) {
        runCatching {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            view.playSoundEffect(SoundEffectConstants.CLICK)
        }
    }

    fun warningOrError(view: View) {
        runCatching {
            view.performHapticFeedback(HapticFeedbackConstants.REJECT)
            val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 45)
            tone.startTone(ToneGenerator.TONE_PROP_NACK, 120)
            view.postDelayed({ runCatching { tone.release() } }, 180L)
        }
    }
}
