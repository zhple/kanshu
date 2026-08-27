package com.kanshu.reader.ui.music

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kanshu.reader.music.MusicController
import com.kanshu.reader.music.MusicPlayerState
import java.util.Locale
import kotlin.math.max

@Composable
fun MiniPlayerBar(
    controller: MusicController,
    onOpenPlaylist: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by controller.state.collectAsStateWithLifecycle()
    if (!state.visible) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.96f)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                        Color.Transparent
                    )
                ),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        CollapsedRow(
            state = state,
            onToggleExpand = controller::toggleExpanded,
            onTogglePlay = controller::togglePlayPause,
            onDismiss = controller::dismissMiniBar,
            onOpenPlaylist = onOpenPlaylist
        )
        AnimatedVisibility(
            visible = state.expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            ExpandedPanel(
                state = state,
                onSeek = controller::seekTo,
                onPrev = controller::previous,
                onNext = controller::next,
                onCollapse = { controller.setExpanded(false) },
                onOpenPlaylist = onOpenPlaylist
            )
        }
    }
}

@Composable
private fun CollapsedRow(
    state: MusicPlayerState,
    onToggleExpand: () -> Unit,
    onTogglePlay: () -> Unit,
    onDismiss: () -> Unit,
    onOpenPlaylist: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onToggleExpand)
        ) {
            Text(
                text = state.title.ifBlank { "未在播放" },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = state.artist.ifBlank { "共享歌单" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onTogglePlay) {
            Icon(
                imageVector = if (state.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (state.playing) "暂停" else "播放"
            )
        }
        IconButton(onClick = onToggleExpand) {
            Icon(
                imageVector = if (state.expanded) {
                    Icons.Default.KeyboardArrowDown
                } else {
                    Icons.Default.KeyboardArrowUp
                },
                contentDescription = if (state.expanded) "收起控制面板" else "展开控制面板"
            )
        }
        IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = "隐藏迷你条")
        }
        TextButton(onClick = onOpenPlaylist) { Text("歌单") }
    }
}

@Composable
private fun ExpandedPanel(
    state: MusicPlayerState,
    onSeek: (Long) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onCollapse: () -> Unit,
    onOpenPlaylist: () -> Unit
) {
    val duration = max(state.durationMs, 1L)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Slider(
            value = (state.positionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f),
            onValueChange = { onSeek((it * duration).toLong()) },
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(formatTime(state.positionMs), style = MaterialTheme.typography.labelSmall)
            Text(
                "${state.queueIndex + 1}/${state.queueSize.coerceAtLeast(1)}",
                style = MaterialTheme.typography.labelSmall
            )
            Text(formatTime(state.durationMs), style = MaterialTheme.typography.labelSmall)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPrev) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "上一首")
            }
            IconButton(onClick = onNext) {
                Icon(Icons.Default.SkipNext, contentDescription = "下一首")
            }
            TextButton(onClick = onOpenPlaylist) { Text("打开歌单") }
            TextButton(onClick = onCollapse) { Text("收起") }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val m = totalSec / 60L
    val s = totalSec % 60L
    return String.format(Locale.US, "%d:%02d", m, s)
}
