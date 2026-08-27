package com.kanshu.reader.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 高级感环境层：流动 mesh 光晕 + 暗角 + 极淡纹理，替代简单色块。
 */
@Composable
fun KanshuAmbientBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val infinite = rememberInfiniteTransition(label = "mesh")
    val t by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(24_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "meshT"
    )
    val t2 by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(16_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "meshT2"
    )

    val bg = MaterialTheme.colorScheme.background
    val sea = MaterialTheme.colorScheme.primary
    val coffee = MaterialTheme.colorScheme.secondary
    val foam = MaterialTheme.colorScheme.tertiary
    val surface = MaterialTheme.colorScheme.surface

    Box(modifier = modifier.fillMaxSize().background(bg)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawMeshBlobs(t, t2, sea, coffee, foam, surface)
            drawVignette()
            drawFineGrain(t2)
        }
        content()
    }
}

private fun DrawScope.drawMeshBlobs(
    t: Float,
    t2: Float,
    sea: Color,
    coffee: Color,
    foam: Color,
    surface: Color
) {
    val w = size.width
    val h = size.height
    val twoPi = (PI * 2).toFloat()

    data class Blob(val cx: Float, val cy: Float, val color: Color, val radius: Float)

    val blobs = listOf(
        Blob(
            0.22f + sin(t * twoPi) * 0.12f,
            0.18f + cos(t * twoPi * 0.7f) * 0.1f,
            sea,
            0.55f
        ),
        Blob(
            0.78f - sin(t2 * twoPi * 0.8f) * 0.1f,
            0.25f + cos(t * twoPi) * 0.08f,
            coffee,
            0.48f
        ),
        Blob(
            0.5f + cos(t2 * twoPi) * 0.15f,
            0.62f + sin(t2 * twoPi * 0.6f) * 0.1f,
            foam,
            0.42f
        ),
        Blob(
            0.35f + sin(t2 * twoPi * 1.2f) * 0.08f,
            0.88f - t * 0.05f,
            sea.copy(alpha = 0.85f),
            0.38f
        ),
        Blob(
            0.65f,
            0.45f + sin(t * twoPi * 0.5f) * 0.06f,
            surface,
            0.35f
        )
    )

    blobs.forEach { blob ->
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    blob.color.copy(alpha = 0.22f),
                    blob.color.copy(alpha = 0.06f),
                    Color.Transparent
                ),
                center = Offset(w * blob.cx, h * blob.cy),
                radius = w * blob.radius
            ),
            radius = w * blob.radius,
            center = Offset(w * blob.cx, h * blob.cy)
        )
    }
}

private fun DrawScope.drawVignette() {
    val w = size.width
    val h = size.height
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.Transparent,
                Color.Black.copy(alpha = 0.06f),
                Color.Black.copy(alpha = 0.14f)
            ),
            center = Offset(w * 0.5f, h * 0.45f),
            radius = w * 0.85f
        )
    )
}

private fun DrawScope.drawFineGrain(phase: Float) {
    val step = 28f
    var y = 0f
    while (y < size.height) {
        var x = (phase * step) % step
        while (x < size.width) {
            drawCircle(
                color = Color.White.copy(alpha = 0.018f),
                radius = 1f,
                center = Offset(x, y)
            )
            x += step
        }
        y += step
    }
}
