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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.PI
import kotlin.math.sin

/**
 * 持续运行的环境层：缓慢漂移的光斑 + 底部海浪，营造海边咖啡馆氛围。
 */
@Composable
fun KanshuAmbientBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val infinite = rememberInfiniteTransition(label = "ambient")
    val drift by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ambientDrift"
    )
    val wavePhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ambientWave"
    )
    val sparkle by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 11_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ambientSparkle"
    )

    val bg = MaterialTheme.colorScheme.background
    val sea = MaterialTheme.colorScheme.primary
    val coffee = MaterialTheme.colorScheme.secondary
    val foam = MaterialTheme.colorScheme.tertiary

    Box(modifier = modifier.fillMaxSize().background(bg)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawAmbientOrbs(drift, sea, coffee, foam)
            drawAmbientWaves(wavePhase, sea, foam)
            drawFloatingDust(sparkle, coffee, foam)
        }
        content()
    }
}

private fun DrawScope.drawAmbientOrbs(
    drift: Float,
    sea: androidx.compose.ui.graphics.Color,
    coffee: androidx.compose.ui.graphics.Color,
    foam: androidx.compose.ui.graphics.Color
) {
    val w = size.width
    val h = size.height
    drawCircle(
        color = sea.copy(alpha = 0.14f),
        radius = w * 0.42f,
        center = Offset(w * (0.18f + drift * 0.2f), h * (0.12f + drift * 0.1f))
    )
    drawCircle(
        color = coffee.copy(alpha = 0.11f),
        radius = w * 0.34f,
        center = Offset(w * (0.82f - drift * 0.18f), h * (0.22f + drift * 0.12f))
    )
    drawCircle(
        color = foam.copy(alpha = 0.09f),
        radius = w * 0.28f,
        center = Offset(w * (0.5f + (drift - 0.5f) * 0.15f), h * (0.55f - drift * 0.08f))
    )
}

private fun DrawScope.drawAmbientWaves(
    wavePhase: Float,
    sea: androidx.compose.ui.graphics.Color,
    foam: androidx.compose.ui.graphics.Color
) {
    val w = size.width
    val h = size.height
    for (layer in 0..2) {
        val baseY = h * (0.82f + layer * 0.05f)
        val amplitude = 10f + layer * 6f
        val path = Path().apply {
            moveTo(0f, baseY)
            var x = 0f
            while (x <= w) {
                val rad = (x / w * 5f * PI + wavePhase + layer * 0.8f).toDouble()
                lineTo(x, baseY + sin(rad).toFloat() * amplitude)
                x += 12f
            }
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        val color = if (layer == 0) sea else foam
        drawPath(path, color.copy(alpha = 0.06f + layer * 0.02f))
    }
}

private fun DrawScope.drawFloatingDust(
    sparkle: Float,
    coffee: androidx.compose.ui.graphics.Color,
    foam: androidx.compose.ui.graphics.Color
) {
    val w = size.width
    val h = size.height
    val dots = 12
    repeat(dots) { i ->
        val t = (sparkle + i / dots.toFloat()) % 1f
        val x = w * ((i * 0.17f + t * 0.25f) % 1f)
        val y = h * (0.92f - t * 0.55f)
        val alpha = (0.04f + (1f - kotlin.math.abs(t - 0.5f) * 2f) * 0.06f).coerceIn(0.02f, 0.1f)
        drawCircle(
            color = if (i % 2 == 0) coffee else foam,
            radius = 2.5f + (i % 3),
            center = Offset(x, y),
            alpha = alpha
        )
    }
}
