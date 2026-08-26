package com.kanshu.reader.ui.music

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kanshu.reader.data.db.TrackEntity
import com.kanshu.reader.data.remote.GithubMusicUploader
import com.kanshu.reader.data.remote.MusicSync
import com.kanshu.reader.data.repo.MusicRepository
import com.kanshu.reader.music.MusicController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

class MusicViewModel(
    private val musicRepository: MusicRepository,
    private val musicController: MusicController,
    private val musicSync: MusicSync,
    private val githubMusicUploader: GithubMusicUploader
) : ViewModel() {
    val tracks: StateFlow<List<TrackEntity>> = musicRepository.observeTracks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun consumeMessage() {
        _message.value = null
    }

    fun importTrack(uri: Uri, contentResolver: android.content.ContentResolver) {
        importTracks(listOf(uri), contentResolver)
    }

    fun importTracks(uris: List<Uri>, contentResolver: android.content.ContentResolver) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val result = musicRepository.importTracks(contentResolver, uris)
            _message.value = when {
                result.success > 0 && result.failed == 0 ->
                    if (result.success == 1) "已加入歌单" else "已加入 ${result.success} 首"
                result.success > 0 && result.failed > 0 ->
                    "已加入 ${result.success} 首，${result.failed} 首失败"
                else -> result.lastError ?: "导入失败"
            }
        }
    }

    fun playAll(start: TrackEntity? = null) {
        val list = tracks.value
        if (list.isEmpty()) {
            _message.value = "歌单还是空的，先导入或同步歌曲"
            return
        }
        if (start == null) musicController.playQueue(list, 0)
        else musicController.playTrack(start, list)
    }

    fun syncRemote() {
        viewModelScope.launch {
            val result = musicSync.sync()
            _message.value = result.message
        }
    }

    fun upload(track: TrackEntity) {
        viewModelScope.launch {
            val result = githubMusicUploader.uploadTrack(track)
            _message.value = result.fold(
                onSuccess = { it },
                onFailure = { it.message ?: "分享失败" }
            )
        }
    }

    fun delete(track: TrackEntity) {
        viewModelScope.launch {
            if (musicController.state.value.trackId == track.id) {
                musicController.hide()
            }
            musicRepository.deleteTrack(track)
            _message.value = "已删除：${track.title}"
        }
    }

    companion object {
        fun factory(
            musicRepository: MusicRepository,
            musicController: MusicController,
            musicSync: MusicSync,
            githubMusicUploader: GithubMusicUploader
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MusicViewModel(
                    musicRepository,
                    musicController,
                    musicSync,
                    githubMusicUploader
                ) as T
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicScreen(
    viewModel: MusicViewModel,
    musicController: MusicController,
    onBack: () -> Unit
) {
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val miniPlayerInset = miniPlayerBottomInset(musicController)

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) viewModel.importTracks(uris, context.contentResolver)
    }

    LaunchedEffect(message) {
        val msg = message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.consumeMessage()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                title = {
                    Column {
                        Text("共享歌单", fontWeight = FontWeight.Bold)
                        Text(
                            "可上传分享，底部迷你播放器随时控制",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::syncRemote) {
                        Icon(Icons.Default.CloudDownload, contentDescription = "同步共享歌单")
                    }
                    IconButton(onClick = { viewModel.playAll() }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "播放全部")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                modifier = Modifier.padding(bottom = miniPlayerInset),
                onClick = {
                    picker.launch(
                        arrayOf(
                            "audio/*",
                            "audio/mpeg",
                            "audio/mp4",
                            "audio/aac",
                            "audio/ogg",
                            "audio/wav",
                            "audio/flac",
                            "application/octet-stream",
                            "*/*"
                        )
                    )
                }
            ) {
                Icon(Icons.Default.LibraryMusic, contentDescription = "导入歌曲")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (tracks.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("歌单还是空的", style = MaterialTheme.typography.titleMedium)
                Text(
                    "点右下角导入本地歌曲（可多选，含 .ncm），或点云朵同步朋友分享的歌单。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 16.dp,
                    bottom = 16.dp + miniPlayerInset
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(tracks, key = { _, t -> t.id }) { index, track ->
                    TrackRow(
                        index = index,
                        track = track,
                        onPlay = { viewModel.playAll(track) },
                        onUpload = { viewModel.upload(track) },
                        onDelete = { viewModel.delete(track) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackRow(
    index: Int,
    track: TrackEntity,
    onPlay: () -> Unit,
    onUpload: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${index + 1}",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(end = 12.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                track.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = buildString {
                    append(track.artist)
                    if (track.durationMs > 0) {
                        append(" · ")
                        append(formatDuration(track.durationMs))
                    }
                    if (track.isRemote) append(" · 共享")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onPlay) {
            Icon(Icons.Default.PlayArrow, contentDescription = "播放")
        }
        IconButton(onClick = onUpload) {
            Icon(Icons.Default.CloudUpload, contentDescription = "分享到远程")
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "删除")
        }
    }
}

private fun formatDuration(ms: Long): String {
    val total = (ms / 1000L).coerceAtLeast(0L)
    return String.format(Locale.US, "%d:%02d", total / 60, total % 60)
}
