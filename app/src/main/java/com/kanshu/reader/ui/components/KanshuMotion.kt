package com.kanshu.reader.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** 列表项轻微错峰淡入，克制不夸张。 */
@Composable
fun Modifier.kanshuStaggerEnter(
    index: Int,
    key: Any = index
): Modifier {
    var shown by remember(key) { mutableStateOf(false) }
    LaunchedEffect(key) {
        delay((index.coerceAtMost(12) * 32).toLong())
        shown = true
    }
    val alpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(380, easing = FastOutSlowInEasing),
        label = "staggerAlpha"
    )
    val offsetY by animateFloatAsState(
        targetValue = if (shown) 0f else 10f,
        animationSpec = tween(380, easing = FastOutSlowInEasing),
        label = "staggerY"
    )
    return this.graphicsLayer {
        this.alpha = alpha
        translationY = offsetY
    }
}

/** 保留接口，简约模式下不做 3D 倾斜。 */
@Composable
fun Modifier.kanshu3DTilt(
    @Suppress("UNUSED_PARAMETER") interactionSource: MutableInteractionSource
): Modifier = this

/** 保留接口，默认关闭封面闪光。 */
@Composable
fun Modifier.kanshuCoverShimmer(): Modifier = this

/** 保留接口，简约模式下 FAB 不漂浮。 */
@Composable
fun Modifier.kanshuFloat(
    @Suppress("UNUSED_PARAMETER") amplitudeDp: Dp = 0.dp,
    @Suppress("UNUSED_PARAMETER") durationMs: Int = 0,
    @Suppress("UNUSED_PARAMETER") phaseOffset: Float = 0f
): Modifier = this

/** 按压轻微缩放。 */
@Composable
fun Modifier.kanshuPressScale(
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = spring(
            stiffness = Spring.StiffnessMedium,
            dampingRatio = Spring.DampingRatioNoBouncy
        ),
        label = "kanshuPressScale"
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
