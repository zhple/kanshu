package com.kanshu.reader.ui.music

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kanshu.reader.music.MusicController

/** 底部迷你播放器占用高度，用于给 FAB / 列表留空。 */
@Composable
fun miniPlayerBottomInset(controller: MusicController): Dp {
    val state by controller.state.collectAsStateWithLifecycle()
    if (!state.visible || state.docked) return 0.dp
    return if (state.expanded) 200.dp else 96.dp
}
