package com.kanshu.reader.data.remote

import com.kanshu.reader.BuildConfig
import com.kanshu.reader.data.repo.MusicRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class MusicSync(
    private val musicRepository: MusicRepository
) {
    data class SyncResult(
        val added: Int = 0,
        val skipped: Int = 0,
        val failed: Int = 0,
        val message: String = ""
    )

    suspend fun sync(): SyncResult = withContext(Dispatchers.IO) {
        val catalog = loadPlaylist()
            ?: return@withContext SyncResult(message = "无法读取远程歌单")
        var added = 0
        var skipped = 0
        var failed = 0
        catalog.forEachIndexed { index, spec ->
            val existing = musicRepository.getByRemoteId(spec.id)
            if (existing != null) {
                skipped++
                return@forEachIndexed
            }
            val ok = runCatching {
                val destName = "remote_${spec.id}${extensionOf(spec.file)}"
                val dest = musicRepository.musicFile(destName)
                downloadRemoteFile(spec.file, dest)
                require(dest.exists() && dest.length() > 0L) { "文件为空" }
                musicRepository.insertRemoteTrack(
                    remoteId = spec.id,
                    title = spec.title,
                    artist = spec.artist,
                    fileName = destName,
                    durationMs = spec.durationMs,
                    sortOrder = index
                )
            }.isSuccess
            if (ok) added++ else failed++
        }
        SyncResult(
            added = added,
            skipped = skipped,
            failed = failed,
            message = when {
                added > 0 -> "已同步 $added 首共享歌曲"
                failed > 0 -> "有 $failed 首同步失败"
                else -> "共享歌单已是最新"
            }
        )
    }

    private fun loadPlaylist(): List<RemoteTrackSpec>? {
        val url = BuildConfig.DEFAULT_MUSIC_CATALOG_URL.trim()
        if (url.isBlank()) return null
        val json = runCatching { httpGet(url) }.getOrNull() ?: return null
        return runCatching { parsePlaylist(json) }.getOrNull()
    }

    private fun parsePlaylist(json: String): List<RemoteTrackSpec> {
        val root = JSONObject(json)
        val arr = root.optJSONArray("tracks") ?: return emptyList()
        val list = mutableListOf<RemoteTrackSpec>()
        for (i in 0 until arr.length()) {
            val item = arr.getJSONObject(i)
            val id = item.optString("id").trim()
            val file = item.optString("file").trim()
            if (id.isBlank() || file.isBlank()) continue
            list += RemoteTrackSpec(
                id = id,
                title = item.optString("title").ifBlank { id },
                artist = item.optString("artist").ifBlank { "共享" },
                file = file,
                durationMs = item.optLong("durationMs", 0L)
            )
        }
        return list
    }

    private fun downloadRemoteFile(fileName: String, dest: File) {
        val base = BuildConfig.DEFAULT_MUSIC_BASE_URL.trimEnd('/')
        require(base.isNotBlank()) { "未配置远程音乐地址" }
        val conn = (URL("$base/$fileName").openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 120_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Kanshu-Music")
        }
        if (conn.responseCode !in 200..299) error("下载失败 HTTP ${conn.responseCode}")
        conn.inputStream.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
    }

    private fun httpGet(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", "Kanshu-Music")
            setRequestProperty("Accept", "application/json")
        }
        if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
        return conn.inputStream.bufferedReader().use { it.readText() }
    }

    private fun extensionOf(file: String): String {
        val fromFile = file.substringAfterLast('.', missingDelimiterValue = "")
        return if (fromFile.isNotBlank()) ".${fromFile.lowercase()}" else ".mp3"
    }
}
