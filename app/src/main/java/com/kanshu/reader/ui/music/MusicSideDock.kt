package com.kanshu.reader.ui.music

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kanshu.reader.music.MusicController

private val DockSize = 52.dp
private val DiscHoleSize = 14.dp

/**
 * 侧边 CD 芯片：收纳态显示在屏幕右侧，播放时旋转，点击展开底部控制条。
 */
@Composable
fun MusicSideDock(
    controller: MusicController,
    modifier: Modifier = Modifier
) {
    val state by controller.state.collectAsStateWithLifecycle()
    val active = state.docked && (state.playing || state.title.isNotBlank())

    AnimatedVisibility(
        visible = active,
        modifier = modifier,
        enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(tween(240)),
        exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(tween(180))
    ) {
        val rotation = if (state.playing) {
            val transition = rememberInfiniteTransition(label = "cd-spin")
            transition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 9_000, easing = LinearEasing)
                ),
                label = "cd-angle"
            ).value
        } else {
            0f
        }

        Box(
            modifier = Modifier
                .size(DockSize)
                .shadow(4.dp, CircleShape, clip = false)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(
                    0.5.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                    CircleShape
                )
                .semantics {
                    contentDescription = if (state.playing) {
                        "音乐播放中，点击展开控制条"
                    } else {
                        "音乐已暂停，点击展开控制条"
                    }
                }
                .clickable(onClick = controller::showPanel),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(DockSize - 8.dp)
                    .graphicsLayer { rotationZ = rotation }
                    .clip(CircleShape)
                    .background(
                        Brush.sweepGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f),
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                            )
                        )
                    )
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                        CircleShape
                    )
            )
            Box(
                modifier = Modifier
                    .size(DiscHoleSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        0.5.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        CircleShape
                    )
            )
        }
    }
}
