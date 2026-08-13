package com.kanshu.reader.data.remote

import android.util.Base64
import com.kanshu.reader.BuildConfig
import com.kanshu.reader.data.db.BookEntity
import com.kanshu.reader.data.repo.BookRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.UUID
import com.kanshu.reader.data.prefs.ThemePreferences

class GithubBooksUploader(
    private val bookRepository: BookRepository,
    private val themePreferences: ThemePreferences
) {
    data class UploadResult(
        val remoteId: String,
        val message: String
    )

    suspend fun uploadBook(book: BookEntity): Result<UploadResult> = withContext(Dispatchers.IO) {
        runCatching {
            val token = themePreferences.githubToken.first().trim()
            require(token.isNotEmpty()) {
                "还没配置上传权限。请点右上角设置，填写 GitHub Token 后再上传。"
            }

            val file = bookRepository.resolveFile(book)
            require(file.exists() && file.length() > 0L) { "本地文件不存在" }
            require(file.length() < 40L * 1024 * 1024) { "文件过大（建议小于 40MB）" }

            val remoteId = book.remoteId?.takeIf { it.isNotBlank() }
                ?: ("user-" + UUID.randomUUID().toString().replace("-", "").take(10))
            val ext = when {
                book.format.equals("PDF", true) -> ".pdf"
                book.format.equals("TXT", true) -> ".txt"
                else -> ".epub"
            }
            val remoteFileName = "$remoteId$ext"
            val path = "default-books/$remoteFileName"

            putContent(
                token = token,
                path = path,
                contentBytes = file.readBytes(),
                message = "Add book: ${book.title}"
            )

            upsertCatalog(
                token = token,
                spec = RemoteBookSpec(
                    id = remoteId,
                    title = book.title,
                    author = book.author.ifBlank { "用户上传" },
                    file = remoteFileName,
                    format = book.format.uppercase()
                )
            )

            val folderId = bookRepository.ensureFolder("仓库书")
            bookRepository.markAsRemote(
                bookId = book.id,
                remoteId = remoteId,
                folderId = folderId
            )

            UploadResult(
                remoteId = remoteId,
                message = "已上传到仓库：${book.title}"
            )
        }
    }

    private fun upsertCatalog(token: String, spec: RemoteBookSpec) {
        val path = "default-books/catalog.json"
        val existing = getContent(token, path)
        val root = if (existing != null) {
            JSONObject(String(existing.content, StandardCharsets.UTF_8))
        } else {
            JSONObject().put("version", 1).put("books", JSONArray())
        }
        val books = root.optJSONArray("books") ?: JSONArray().also { root.put("books", it) }
        var found = false
        for (i in 0 until books.length()) {
            val item = books.getJSONObject(i)
            if (item.optString("id") == spec.id) {
                item.put("title", spec.title)
                item.put("author", spec.author)
                item.put("file", spec.file)
                item.put("format", spec.format)
                found = true
                break
            }
        }
        if (!found) {
            books.put(
                JSONObject()
                    .put("id", spec.id)
                    .put("title", spec.title)
                    .put("author", spec.author)
                    .put("file", spec.file)
                    .put("format", spec.format)
            )
        }
        root.put("version", root.optInt("version", 1) + if (found) 0 else 1)
        val bytes = root.toString(2).toByteArray(StandardCharsets.UTF_8)
        putContent(
            token = token,
            path = path,
            contentBytes = bytes,
            message = "Update catalog for ${spec.id}",
            sha = existing?.sha
        )
    }

    private data class RepoFile(val content: ByteArray, val sha: String)

    private fun getContent(token: String, path: String): RepoFile? {
        val url = apiUrl(path)
        val conn = open(url, token, "GET")
        return when (conn.responseCode) {
            200 -> {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val b64 = json.optString("content").replace("\n", "")
                val sha = json.optString("sha")
                val bytes = Base64.decode(b64, Base64.DEFAULT)
                RepoFile(bytes, sha)
            }
            404 -> null
            else -> {
                val body = readError(conn)
                if (conn.responseCode == 401 || body.contains("Bad credentials", ignoreCase = true)) {
                    error("上传权限失效（Token 无效或过期）。请点右上角设置，重新填写 GitHub Token。")
                }
                error("读取仓库失败 HTTP ${conn.responseCode}: ${body.take(200)}")
            }
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
        if (!currentSha.isNullOrBlank()) {
            payload.put("sha", currentSha)
        }
        val conn = open(apiUrl(path), token, "PUT")
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        conn.outputStream.use { it.write(payload.toString().toByteArray(StandardCharsets.UTF_8)) }
        if (conn.responseCode !in 200..299) {
            val body = readError(conn)
            if (conn.responseCode == 401 || body.contains("Bad credentials", ignoreCase = true)) {
                error("上传权限失效（Token 无效或过期）。请点右上角设置，重新填写 GitHub Token。")
            }
            error("上传失败 HTTP ${conn.responseCode}: ${body.take(200)}")
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
            setRequestProperty("User-Agent", "Kanshu-Uploader")
        }
    }

    private fun readError(conn: HttpURLConnection): String {
        return runCatching {
            (conn.errorStream ?: conn.inputStream)?.bufferedReader()?.use { it.readText() }
        }.getOrNull().orEmpty().take(300)
    }
}
