package com.azuredevid.cassy.ui.scanner

import android.graphics.Rect
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.azuredevid.cassy.R
import com.azuredevid.cassy.ui.designsystem.CassyDimens
import com.azuredevid.cassy.ui.theme.BrandPanelPandan
import com.azuredevid.cassy.ui.theme.BrandStatusOnlineDot
import com.azuredevid.cassy.ui.theme.CassyPandan
import com.azuredevid.cassy.ui.theme.CassyTinta
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun CameraQrScannerView(
    onQrDetected: (String) -> Unit,
    onCloseCamera: () -> Unit,
    onOpenHidScanner: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val currentOnQrDetected by rememberUpdatedState(onQrDetected)
    val detectedLock = remember { AtomicBoolean(false) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val barcodeScanner = remember { BarcodeScanning.getClient() }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var previewViewState by remember { mutableStateOf<PreviewView?>(null) }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    var hasFlashUnit by remember { mutableStateOf(false) }
    var isTorchOn by remember { mutableStateOf(false) }
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var matchConfirmed by remember { mutableStateOf(false) }

    val scanTransition = rememberInfiniteTransition(label = "qrLaserLine")
    val scanLineProgress by scanTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "qrLaserProgress"
    )

    LaunchedEffect(isTorchOn, boundCamera) {
        boundCamera?.cameraControl?.enableTorch(isTorchOn)
    }

    LaunchedEffect(cameraProvider, previewViewState, lensFacing, lifecycleOwner) {
        val provider = cameraProvider ?: return@LaunchedEffect
        val previewView = previewViewState ?: return@LaunchedEffect

        val preview = Preview.Builder().build().also {
            it.surfaceProvider = previewView.surfaceProvider
        }

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
            val mediaImage = imageProxy.image
            if (mediaImage != null && !detectedLock.get()) {
                val rotation = imageProxy.imageInfo.rotationDegrees
                val frameWidth = if (rotation == 90 || rotation == 270) imageProxy.height else imageProxy.width
                val frameHeight = if (rotation == 90 || rotation == 270) imageProxy.width else imageProxy.height
                val image = InputImage.fromMediaImage(mediaImage, rotation)
                barcodeScanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        for (barcode in barcodes) {
                            val validType = barcode.valueType == Barcode.TYPE_TEXT ||
                                barcode.valueType == Barcode.TYPE_URL
                            val rawValue = barcode.rawValue
                            if (validType &&
                                !rawValue.isNullOrBlank() &&
                                isInsideCenterRoi(barcode.boundingBox, frameWidth, frameHeight) &&
                                detectedLock.compareAndSet(false, true)
                            ) {
                                scope.launch {
                                    matchConfirmed = true
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    delay(150L)
                                    currentOnQrDetected(rawValue)
                                }
                                break
                            }
                        }
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            } else {
                imageProxy.close()
            }
        }

        val selector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        runCatching {
            provider.unbindAll()
            isTorchOn = false
            val camera = provider.bindToLifecycle(
                lifecycleOwner,
                selector,
                preview,
                imageAnalysis
            )
            boundCamera = camera
            hasFlashUnit = camera.cameraInfo.hasFlashUnit()
        }
    }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            runCatching { boundCamera?.cameraControl?.enableTorch(false) }
            runCatching { cameraProvider?.unbindAll() }
            runCatching { barcodeScanner.close() }
            runCatching { cameraExecutor.shutdownNow() }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).also { previewView ->
                    previewViewState = previewView
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        cameraProvider = runCatching { cameraProviderFuture.get() }.getOrNull()
                    }, ContextCompat.getMainExecutor(ctx))
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Immersive outer vignette scrim with transparent center viewfinder, Pandan brackets & laser line.
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        ) {
            val boxSizePx = 280.dp.toPx().coerceAtMost(minOf(size.width, size.height) * 0.68f)
            val left = (size.width - boxSizePx) / 2f
            val top = (size.height - boxSizePx) / 2f
            val cornerRadiusPx = 24.dp.toPx()

            drawRect(color = Color.Black.copy(alpha = 0.58f))
            drawRoundRect(
                color = Color.Transparent,
                topLeft = Offset(left, top),
                size = Size(boxSizePx, boxSizePx),
                cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                blendMode = BlendMode.Clear
            )

            val bracketColor = if (matchConfirmed) BrandStatusOnlineDot else CassyPandan
            drawCornerBrackets(
                left = left,
                top = top,
                boxSize = boxSizePx,
                bracketLen = 34.dp.toPx(),
                strokeWidth = 4.5.dp.toPx(),
                color = bracketColor
            )

            val laserInset = 20.dp.toPx()
            val laserY = top + laserInset + (boxSizePx - laserInset * 2f) * scanLineProgress
            drawLine(
                color = bracketColor.copy(alpha = 0.85f),
                start = Offset(left + laserInset, laserY),
                end = Offset(left + boxSizePx - laserInset, laserY),
                strokeWidth = 2.5.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        // Top translucent navigation & camera hardware controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = CassyDimens.SpaceXl, vertical = CassyDimens.SpaceLg)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onCloseCamera,
                modifier = Modifier
                    .size(CassyDimens.TouchTargetMin)
                    .background(CassyTinta.copy(alpha = 0.68f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.scanner_close_cd),
                    tint = Color.White
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceSm)) {
                if (hasFlashUnit) {
                    IconButton(
                        onClick = { isTorchOn = !isTorchOn },
                        modifier = Modifier
                            .size(CassyDimens.TouchTargetMin)
                            .background(
                                color = if (isTorchOn) BrandPanelPandan else CassyTinta.copy(alpha = 0.68f),
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = if (isTorchOn) Icons.Rounded.FlashOff else Icons.Rounded.FlashOn,
                            contentDescription = stringResource(
                                if (isTorchOn) R.string.scanner_torch_off else R.string.scanner_torch_on
                            ),
                            tint = Color.White
                        )
                    }
                }
                IconButton(
                    onClick = {
                        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                            CameraSelector.LENS_FACING_FRONT
                        } else {
                            CameraSelector.LENS_FACING_BACK
                        }
                    },
                    modifier = Modifier
                        .size(CassyDimens.TouchTargetMin)
                        .background(CassyTinta.copy(alpha = 0.68f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Cameraswitch,
                        contentDescription = stringResource(R.string.scanner_flip_camera),
                        tint = Color.White
                    )
                }
            }
        }

        // Single unified instruction pill above bottom controls
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = CassyDimens.SpaceXl, vertical = CassyDimens.Space2Xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(CassyDimens.SpaceMd)
        ) {
            Surface(
                shape = RoundedCornerShape(CassyDimens.RadiusBadge),
                color = CassyTinta.copy(alpha = 0.78f)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = CassyDimens.SpaceLg, vertical = CassyDimens.SpaceSm),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.scanner_reticle_hint),
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = stringResource(R.string.pairing_camera_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center
                    )
                }
            }

            OutlinedButton(
                onClick = onOpenHidScanner,
                shape = RoundedCornerShape(CassyDimens.RadiusPill),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.White.copy(alpha = 0.94f),
                    contentColor = CassyTinta
                ),
                border = null,
                contentPadding = PaddingValues(
                    horizontal = CassyDimens.SpaceXl,
                    vertical = CassyDimens.SpaceSm
                ),
                modifier = Modifier.defaultMinSize(
                    minWidth = CassyDimens.TouchTargetMin,
                    minHeight = CassyDimens.TouchTargetMin
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Keyboard,
                    contentDescription = null,
                    modifier = Modifier.size(CassyDimens.IconSizeSm)
                )
                Spacer(Modifier.width(CassyDimens.SpaceSm))
                Text(
                    text = stringResource(R.string.pairing_btn_hid_scanner),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

private fun isInsideCenterRoi(
    boundingBox: Rect?,
    frameWidth: Int,
    frameHeight: Int
): Boolean {
    if (boundingBox == null || frameWidth <= 0 || frameHeight <= 0) return true
    val cx = boundingBox.exactCenterX()
    val cy = boundingBox.exactCenterY()
    val minX = frameWidth * 0.14f
    val maxX = frameWidth * 0.86f
    val minY = frameHeight * 0.14f
    val maxY = frameHeight * 0.86f
    return cx in minX..maxX && cy in minY..maxY
}

private fun DrawScope.drawCornerBrackets(
    left: Float,
    top: Float,
    boxSize: Float,
    bracketLen: Float,
    strokeWidth: Float,
    color: Color
) {
    val right = left + boxSize
    val bottom = top + boxSize
    val strokeCap = StrokeCap.Round

    // Top-left
    drawLine(color, Offset(left, top + bracketLen), Offset(left, top), strokeWidth, strokeCap)
    drawLine(color, Offset(left, top), Offset(left + bracketLen, top), strokeWidth, strokeCap)
    // Top-right
    drawLine(color, Offset(right - bracketLen, top), Offset(right, top), strokeWidth, strokeCap)
    drawLine(color, Offset(right, top), Offset(right, top + bracketLen), strokeWidth, strokeCap)
    // Bottom-left
    drawLine(color, Offset(left, bottom - bracketLen), Offset(left, bottom), strokeWidth, strokeCap)
    drawLine(color, Offset(left, bottom), Offset(left + bracketLen, bottom), strokeWidth, strokeCap)
    // Bottom-right
    drawLine(color, Offset(right - bracketLen, bottom), Offset(right, bottom), strokeWidth, strokeCap)
    drawLine(color, Offset(right, bottom - bracketLen), Offset(right, bottom), strokeWidth, strokeCap)
}
