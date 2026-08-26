package com.kanshu.reader.music

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.kanshu.reader.data.db.TrackEntity
import com.kanshu.reader.data.repo.MusicRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class MusicPlayerState(
    val visible: Boolean = false,
    val expanded: Boolean = false,
    val playing: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val trackId: Long? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val queueSize: Int = 0,
    val queueIndex: Int = 0
)

/**
 * 应用级播放器：跨页面保活，配合底部可折叠迷你条。
 */
class MusicController(
    context: Context,
    private val musicRepository: MusicRepository
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val player: ExoPlayer = ExoPlayer.Builder(appContext).build()

    private val _state = MutableStateFlow(MusicPlayerState())
    val state: StateFlow<MusicPlayerState> = _state.asStateFlow()

    private var queue: List<TrackEntity> = emptyList()
    private var progressJob: Job? = null

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.update { it.copy(playing = isPlaying) }
                if (isPlaying) startProgressLoop() else progressJob?.cancel()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    next()
                }
                publishProgress()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val idx = player.currentMediaItemIndex
                val track = queue.getOrNull(idx)
                _state.update {
                    it.copy(
                        trackId = track?.id,
                        title = track?.title.orEmpty(),
                        artist = track?.artist.orEmpty(),
                        queueIndex = idx.coerceAtLeast(0),
                        durationMs = track?.durationMs ?: player.duration.coerceAtLeast(0L)
                    )
                }
                publishProgress()
            }
        })
    }

    fun playQueue(tracks: List<TrackEntity>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        val playable = tracks.mapNotNull { track ->
            val file = musicRepository.resolveFile(track)
            if (file.exists()) track to file else null
        }
        if (playable.isEmpty()) return
        queue = playable.map { it.first }
        val items = playable.map { (_, file) ->
            MediaItem.fromUri(Uri.fromFile(file))
        }
        val index = startIndex.coerceIn(0, items.lastIndex)
        player.setMediaItems(items, index, 0L)
        player.prepare()
        player.play()
        val track = queue[index]
        _state.update {
            it.copy(
                visible = true,
                expanded = it.expanded,
                playing = true,
                title = track.title,
                artist = track.artist,
                trackId = track.id,
                queueSize = queue.size,
                queueIndex = index,
                durationMs = track.durationMs,
                positionMs = 0L
            )
        }
        startProgressLoop()
    }

    fun playTrack(track: TrackEntity, all: List<TrackEntity>) {
        val index = all.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        playQueue(all, index)
    }

    fun togglePlayPause() {
        if (!_state.value.visible) return
        if (player.isPlaying) player.pause() else player.play()
    }

    fun next() {
        if (queue.isEmpty()) return
        if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
            player.play()
        } else {
            player.seekTo(0, 0L)
            player.play()
        }
    }

    fun previous() {
        if (queue.isEmpty()) return
        if (player.currentPosition > 3000L) {
            player.seekTo(0L)
        } else if (player.hasPreviousMediaItem()) {
            player.seekToPreviousMediaItem()
            player.play()
        } else {
            player.seekTo(0L)
        }
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs.coerceAtLeast(0L))
        publishProgress()
    }

    fun setExpanded(expanded: Boolean) {
        _state.update { it.copy(expanded = expanded) }
    }

    fun toggleExpanded() {
        _state.update { it.copy(expanded = !it.expanded) }
    }

    fun collapseAndKeepPlaying() {
        _state.update { it.copy(expanded = false) }
    }

    fun hide() {
        player.pause()
        _state.update { it.copy(visible = false, expanded = false, playing = false) }
    }

    fun release() {
        progressJob?.cancel()
        player.release()
    }

    private fun startProgressLoop() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                publishProgress()
                delay(500L)
            }
        }
    }

    private fun publishProgress() {
        val duration = when {
            player.duration > 0 -> player.duration
            else -> _state.value.durationMs
        }
        _state.update {
            it.copy(
                positionMs = player.currentPosition.coerceAtLeast(0L),
                durationMs = duration.coerceAtLeast(0L),
                playing = player.isPlaying
            )
        }
    }
}
