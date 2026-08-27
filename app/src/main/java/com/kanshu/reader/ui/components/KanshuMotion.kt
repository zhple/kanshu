package com.kanshu.reader.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** 列表项错峰淡入上浮，进入页面更有节奏感。 */
@Composable
fun Modifier.kanshuStaggerEnter(
    index: Int,
    key: Any = index
): Modifier {
    var shown by remember(key) { mutableStateOf(false) }
    LaunchedEffect(key) {
        delay((index.coerceAtMost(16) * 48).toLong())
        shown = true
    }
    val alpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(520, easing = FastOutSlowInEasing),
        label = "staggerAlpha"
    )
    val offsetY by animateFloatAsState(
        targetValue = if (shown) 0f else 24f,
        animationSpec = spring(
            dampingRatio = 0.82f,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
        ),
        label = "staggerY"
    )
    return this.graphicsLayer {
        this.alpha = alpha
        translationY = offsetY
    }
}

/** 按压时轻微 3D 倾斜，书封更有实体感。 */
@Composable
fun Modifier.kanshu3DTilt(
    interactionSource: MutableInteractionSource
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val density = LocalDensity.current
    val rotY by animateFloatAsState(
        targetValue = if (pressed) 10f else 0f,
        animationSpec = spring(dampingRatio = 0.65f),
        label = "tiltY"
    )
    val rotX by animateFloatAsState(
        targetValue = if (pressed) -5f else 0f,
        animationSpec = spring(dampingRatio = 0.65f),
        label = "tiltX"
    )
    return this.graphicsLayer {
        rotationY = rotY
        rotationX = rotX
        cameraDistance = 10f * density.density
    }
}

/** 封面光泽扫过动画。 */
@Composable
fun Modifier.kanshuCoverShimmer(): Modifier {
    val infinite = rememberInfiniteTransition(label = "coverShimmer")
    val sweep by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "coverSweep"
    )
    return this.graphicsLayer {
        // 轻微亮度脉冲
        val pulse = 0.94f + sweep * 0.06f
        scaleX = pulse
        scaleY = pulse
    }
}

/** 持续上下漂浮，用于 FAB 等控件。 */
@Composable
fun Modifier.kanshuFloat(
    amplitudeDp: Dp = 5.dp,
    durationMs: Int = 2800,
    phaseOffset: Float = 0f
): Modifier {
    val infinite = rememberInfiniteTransition(label = "float")
    val bob by infinite.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = durationMs,
                delayMillis = (phaseOffset * durationMs).toInt(),
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatBob"
    )
    return this.offset(y = amplitudeDp * bob)
}

/** 按压时轻微缩放。 */
@Composable
fun Modifier.kanshuPressScale(
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(
            stiffness = Spring.StiffnessMedium,
            dampingRatio = Spring.DampingRatioMediumBouncy
        ),
        label = "kanshuPressScale"
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
