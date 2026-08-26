package com.kanshu.reader.data.remote

import android.util.Base64
import com.kanshu.reader.BuildConfig
import com.kanshu.reader.data.db.TrackEntity
import com.kanshu.reader.data.prefs.ThemePreferences
import com.kanshu.reader.data.repo.MusicRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.UUID

data class RemoteTrackSpec(
    val id: String,
    val title: String,
    val artist: String,
    val file: String,
    val durationMs: Long = 0L
)

class GithubMusicUploader(
    private val musicRepository: MusicRepository,
    private val themePreferences: ThemePreferences
) {
    suspend fun uploadTrack(track: TrackEntity): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val token = requireToken()
            val file = musicRepository.resolveFile(track)
            require(file.exists() && file.length() > 0L) { "本地音频不存在" }
            require(file.length() < 40L * 1024 * 1024) { "文件过大（建议小于 40MB）" }

            val remoteId = track.remoteId?.takeIf { it.isNotBlank() }
                ?: ("music-" + UUID.randomUUID().toString().replace("-", "").take(10))
            val ext = "." + file.extension.lowercase().ifBlank { "mp3" }
            val remoteFileName = "$remoteId$ext"
            putContent(
                token = token,
                path = "default-music/$remoteFileName",
                contentBytes = file.readBytes(),
                message = "Add music: ${track.title}"
            )
            upsertPlaylist(
                token = token,
                spec = RemoteTrackSpec(
                    id = remoteId,
                    title = track.title,
                    artist = track.artist,
                    file = remoteFileName,
                    durationMs = track.durationMs
                )
            )
            musicRepository.markRemote(track.id, remoteId)
            "已分享到远程歌单：${track.title}"
        }
    }

    private fun upsertPlaylist(token: String, spec: RemoteTrackSpec) {
        val path = "default-music/playlist.json"
        val existing = getContent(token, path)
        val root = if (existing != null) {
            JSONObject(String(existing.content, StandardCharsets.UTF_8))
        } else {
            JSONObject().put("version", 1).put("tracks", JSONArray())
        }
        val tracks = root.optJSONArray("tracks") ?: JSONArray().also { root.put("tracks", it) }
        var found = false
        for (i in 0 until tracks.length()) {
            val item = tracks.getJSONObject(i)
            if (item.optString("id") == spec.id) {
                item.put("title", spec.title)
                item.put("artist", spec.artist)
                item.put("file", spec.file)
                item.put("durationMs", spec.durationMs)
                found = true
                break
            }
        }
        if (!found) {
            tracks.put(
                JSONObject()
                    .put("id", spec.id)
                    .put("title", spec.title)
                    .put("artist", spec.artist)
                    .put("file", spec.file)
                    .put("durationMs", spec.durationMs)
            )
            root.put("version", root.optInt("version", 1) + 1)
        }
        putContent(
            token = token,
            path = path,
            contentBytes = root.toString(2).toByteArray(StandardCharsets.UTF_8),
            message = "Update playlist for ${spec.id}",
            sha = existing?.sha
        )
    }

    private suspend fun requireToken(): String {
        val token = themePreferences.githubToken.first().trim()
        require(token.isNotEmpty()) {
            "还没配置上传权限。请在书架设置里填写 GitHub Token。"
        }
        return token
    }

    private data class RepoFile(val content: ByteArray, val sha: String)

    private fun getContent(token: String, path: String): RepoFile? {
        val conn = open(apiUrl(path), token, "GET")
        return when (conn.responseCode) {
            200 -> {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val b64 = json.optString("content").replace("\n", "")
                val sha = json.optString("sha")
                RepoFile(Base64.decode(b64, Base64.DEFAULT), sha)
            }
            404 -> null
            else -> error("读取仓库失败 HTTP ${conn.responseCode}")
        }
    }

    private fun putContent(
        token: String,
        path: String,
        contentBytes: ByteArray,
        message: String,
        sha: String? = null
    ) {
        val currentSha = sha ?: getContent(token, path)?.sha
        val payload = JSONObject()
            .put("message", message)
            .put("branch", BuildConfig.GITHUB_BRANCH)
            .put("content", Base64.encodeToString(contentBytes, Base64.NO_WRAP))
        if (!currentSha.isNullOrBlank()) payload.put("sha", currentSha)
        val conn = open(apiUrl(path), token, "PUT")
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        conn.outputStream.use { it.write(payload.toString().toByteArray(StandardCharsets.UTF_8)) }
        if (conn.responseCode !in 200..299) {
            error("上传失败 HTTP ${conn.responseCode}")
        }
    }

    private fun apiUrl(path: String): String {
        val owner = BuildConfig.GITHUB_OWNER
        val repo = BuildConfig.GITHUB_REPO
        return "https://api.github.com/repos/$owner/$repo/contents/$path"
    }

    private fun open(url: String, token: String, method: String): HttpURLConnection {
        return (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000
            readTimeout = 120_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "Kanshu-Music")
        }
    }
}
