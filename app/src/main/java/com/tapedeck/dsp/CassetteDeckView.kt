package com.tapedeck.dsp

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private const val REEL_RADIUS_MAX_FRACTION = 0.30f
private const val REEL_RADIUS_MIN_FRACTION = 0.12f

// Arbitrary "tape speed" tuned only for a pleasing on-screen spin rate, not a
// physical unit - what matters is that angular velocity = speed / radius, so
// the shrinking reel visibly spins faster as the growing one slows down.
private const val TAPE_LINEAR_SPEED = 0.16f

private val bodyColor = Color(0xFF2B2118)
private val windowColor = Color(0xFF12100C)
private val tapeColor = Color(0xFF3A2F22)
private val hubColor = Color(0xFFD9A441)
private val spokeColor = Color(0xFF12100C)
private val labelColor = Color(0xFFEDE3D0)
private val meterTrackColor = Color(0xFF3A2F22)
private val meterHotColor = Color(0xFFD9534F)

@Composable
fun CassetteDeckView(
    isPlaying: Boolean,
    progressFraction: Float,
    vuLeft: Float,
    vuRight: Float,
    albumArt: ImageBitmap? = null,
    modifier: Modifier = Modifier,
) {
    var leftRotationDeg by remember { mutableFloatStateOf(0f) }
    var rightRotationDeg by remember { mutableFloatStateOf(0f) }
    val currentProgress = rememberUpdatedState(progressFraction)

    LaunchedEffect(isPlaying) {
        if (!isPlaying) return@LaunchedEffect
        var lastFrameNanos = 0L
        while (true) {
            withFrameNanos { frameNanos ->
                if (lastFrameNanos != 0L) {
                    val dt = (frameNanos - lastFrameNanos) / 1_000_000_000f
                    val progress = currentProgress.value
                    val leftRadius = depletingReelRadius(REEL_RADIUS_MAX_FRACTION, REEL_RADIUS_MIN_FRACTION, progress)
                    val rightRadius = fillingReelRadius(REEL_RADIUS_MAX_FRACTION, REEL_RADIUS_MIN_FRACTION, progress)
                    leftRotationDeg += Math.toDegrees((TAPE_LINEAR_SPEED / leftRadius).toDouble()).toFloat() * dt
                    rightRotationDeg += Math.toDegrees((TAPE_LINEAR_SPEED / rightRadius).toDouble()).toFloat() * dt
                }
                lastFrameNanos = frameNanos
            }
        }
    }

    val animatedVuLeft by animateFloatAsState(targetValue = vuLeft.coerceIn(0f, 1f), label = "vuLeft")
    val animatedVuRight by animateFloatAsState(targetValue = vuRight.coerceIn(0f, 1f), label = "vuRight")

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.7f)
                .padding(12.dp)
        ) {
            val w = size.width
            val h = size.height

            if (albumArt != null) {
                drawCoverImage(albumArt)
                drawRect(color = Color.Black.copy(alpha = 0.5f))
            }

            val shellColor = if (albumArt != null) bodyColor.copy(alpha = 0.55f) else bodyColor
            drawRoundRect(color = shellColor, cornerRadius = CornerRadius(24f, 24f))
            drawRoundRect(
                color = windowColor,
                topLeft = Offset(w * 0.08f, h * 0.10f),
                size = Size(w * 0.84f, h * 0.58f),
                cornerRadius = CornerRadius(16f, 16f),
            )

            val centerY = h * 0.39f
            val leftCenter = Offset(w * 0.28f, centerY)
            val rightCenter = Offset(w * 0.72f, centerY)
            val maxRadius = min(w, h) * REEL_RADIUS_MAX_FRACTION
            val minRadius = min(w, h) * REEL_RADIUS_MIN_FRACTION
            val leftRadius = depletingReelRadius(maxRadius, minRadius, progressFraction)
            val rightRadius = fillingReelRadius(maxRadius, minRadius, progressFraction)

            drawReel(leftCenter, leftRadius, leftRotationDeg)
            drawReel(rightCenter, rightRadius, rightRotationDeg)

            drawRoundRect(
                color = Color(0xFF171310),
                topLeft = Offset(w * 0.12f, h * 0.74f),
                size = Size(w * 0.76f, h * 0.10f),
                cornerRadius = CornerRadius(6f, 6f),
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            VuBar(level = animatedVuLeft, label = "L", modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(16.dp))
            VuBar(level = animatedVuRight, label = "R", modifier = Modifier.weight(1f))
        }
    }
}

// Reel "fullness" reads visually as area, not radius, and area scales with
// radius^2 - interpolating radius directly makes the full (large) reel's
// shrink look barely perceptible while the empty (small) reel's growth
// looks dramatic, even though the same amount of tape moved. Interpolating
// area linearly instead keeps the two reels' visual change symmetric.
private fun depletingReelRadius(maxRadius: Float, minRadius: Float, fraction: Float): Float {
    val maxArea = maxRadius * maxRadius
    val minArea = minRadius * minRadius
    return sqrt(maxArea - (maxArea - minArea) * fraction)
}

private fun fillingReelRadius(maxRadius: Float, minRadius: Float, fraction: Float): Float {
    val maxArea = maxRadius * maxRadius
    val minArea = minRadius * minRadius
    return sqrt(minArea + (maxArea - minArea) * fraction)
}

private fun DrawScope.drawCoverImage(image: ImageBitmap) {
    val canvasAspect = size.width / size.height
    val imageAspect = image.width.toFloat() / image.height.toFloat()

    val srcWidth: Int
    val srcHeight: Int
    val srcOffsetX: Int
    val srcOffsetY: Int

    if (imageAspect > canvasAspect) {
        srcHeight = image.height
        srcWidth = (image.height * canvasAspect).toInt().coerceIn(1, image.width)
        srcOffsetX = (image.width - srcWidth) / 2
        srcOffsetY = 0
    } else {
        srcWidth = image.width
        srcHeight = (image.width / canvasAspect).toInt().coerceIn(1, image.height)
        srcOffsetX = 0
        srcOffsetY = (image.height - srcHeight) / 2
    }

    drawImage(
        image = image,
        srcOffset = IntOffset(srcOffsetX, srcOffsetY),
        srcSize = IntSize(srcWidth, srcHeight),
        dstSize = IntSize(size.width.toInt(), size.height.toInt()),
    )
}

private fun DrawScope.drawReel(center: Offset, radius: Float, rotationDeg: Float) {
    drawCircle(color = tapeColor, radius = radius, center = center)
    drawCircle(color = hubColor, radius = radius * 0.32f, center = center)
    drawCircle(color = windowColor, radius = radius * 0.10f, center = center)

    val spokeCount = 6
    for (i in 0 until spokeCount) {
        val angle = Math.toRadians((rotationDeg + i * (360f / spokeCount)).toDouble())
        val inner = Offset(
            center.x + (radius * 0.14f * cos(angle)).toFloat(),
            center.y + (radius * 0.14f * sin(angle)).toFloat(),
        )
        val outer = Offset(
            center.x + (radius * 0.30f * cos(angle)).toFloat(),
            center.y + (radius * 0.30f * sin(angle)).toFloat(),
        )
        drawLine(color = spokeColor, start = inner, end = outer, strokeWidth = radius * 0.05f)
    }
}

@Composable
private fun VuBar(level: Float, label: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, color = labelColor, modifier = Modifier.width(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(10.dp)
        ) {
            drawRoundRect(color = meterTrackColor, cornerRadius = CornerRadius(4f, 4f))
            val activeWidth = size.width * level
            val meterColor = if (level > 0.85f) meterHotColor else hubColor
            drawRoundRect(
                color = meterColor,
                size = Size(activeWidth, size.height),
                cornerRadius = CornerRadius(4f, 4f),
            )
        }
    }
}
