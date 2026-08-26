package com.kanshu.reader.data.repo

import android.content.ContentResolver
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.kanshu.reader.data.db.MusicDao
import com.kanshu.reader.data.db.TrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class MusicRepository(
    private val musicDao: MusicDao,
    private val filesDir: File
) {
    private val musicDir: File
        get() = File(filesDir, "music").also { if (!it.exists()) it.mkdirs() }

    fun observeTracks(): Flow<List<TrackEntity>> = musicDao.observeTracks()

    suspend fun getAllOnce(): List<TrackEntity> = musicDao.getAllOnce()

    suspend fun getTrack(id: Long): TrackEntity? = musicDao.getById(id)

    suspend fun getByRemoteId(remoteId: String): TrackEntity? = musicDao.getByRemoteId(remoteId)

    fun resolveFile(track: TrackEntity): File = File(musicDir, track.fileName)

    fun musicFile(name: String): File = File(musicDir, name)

    suspend fun importTrack(
        contentResolver: ContentResolver,
        uri: Uri
    ): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val displayName = queryDisplayName(contentResolver, uri) ?: "track.mp3"
            val ext = extensionOf(displayName, contentResolver.getType(uri))
            require(ext in setOf(".mp3", ".m4a", ".aac", ".ogg", ".wav", ".flac")) {
                "暂支持 mp3 / m4a / aac / ogg / wav / flac"
            }
            val safeName = UUID.randomUUID().toString() + ext
            val dest = File(musicDir, safeName)
            contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            } ?: error("无法读取所选音频")
            require(dest.exists() && dest.length() > 0L) { "音频保存失败" }
            require(dest.length() < 40L * 1024 * 1024) { "文件过大（建议小于 40MB，便于共享上传）" }

            val meta = readMeta(dest, displayName)
            val order = musicDao.maxSortOrder() + 1
            musicDao.insert(
                TrackEntity(
                    title = meta.first,
                    artist = meta.second,
                    fileName = safeName,
                    durationMs = meta.third,
                    sortOrder = order,
                    source = TrackEntity.SOURCE_LOCAL
                )
            )
        }
    }

    suspend fun insertRemoteTrack(
        remoteId: String,
        title: String,
        artist: String,
        fileName: String,
        durationMs: Long,
        sortOrder: Int
    ): Long = withContext(Dispatchers.IO) {
        musicDao.insert(
            TrackEntity(
                title = title,
                artist = artist,
                fileName = fileName,
                durationMs = durationMs,
                sortOrder = sortOrder,
                remoteId = remoteId,
                source = TrackEntity.SOURCE_REMOTE
            )
        )
    }

    suspend fun markRemote(id: Long, remoteId: String) = withContext(Dispatchers.IO) {
        musicDao.markRemote(id, remoteId)
    }

    suspend fun deleteTrack(track: TrackEntity) = withContext(Dispatchers.IO) {
        File(musicDir, track.fileName).delete()
        musicDao.delete(track.id)
    }

    private fun readMeta(file: File, fallbackName: String): Triple<String, String, Long> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.trim()
                .orEmpty()
                .ifBlank {
                    fallbackName.substringBeforeLast('.').ifBlank { "未命名歌曲" }
                }
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?.trim()
                .orEmpty()
                .ifBlank { "未知" }
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?: 0L
            Triple(title, artist, duration)
        } catch (_: Exception) {
            Triple(
                fallbackName.substringBeforeLast('.').ifBlank { "未命名歌曲" },
                "未知",
                0L
            )
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun extensionOf(name: String, mime: String?): String {
        val lower = name.lowercase()
        return when {
            lower.endsWith(".mp3") -> ".mp3"
            lower.endsWith(".m4a") -> ".m4a"
            lower.endsWith(".aac") -> ".aac"
            lower.endsWith(".ogg") -> ".ogg"
            lower.endsWith(".wav") -> ".wav"
            lower.endsWith(".flac") -> ".flac"
            mime?.contains("mpeg") == true -> ".mp3"
            mime?.contains("mp4") == true || mime?.contains("m4a") == true -> ".m4a"
            mime?.contains("ogg") == true -> ".ogg"
            mime?.contains("wav") == true -> ".wav"
            mime?.contains("flac") == true -> ".flac"
            else -> ".mp3"
        }
    }

    private fun queryDisplayName(contentResolver: ContentResolver, uri: Uri): String? {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) return cursor.getString(index)
                }
            }
        return uri.lastPathSegment
    }
}
