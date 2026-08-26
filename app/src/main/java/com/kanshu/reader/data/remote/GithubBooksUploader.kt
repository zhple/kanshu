package com.kanshu.reader.data.remote

import android.util.Base64
import com.kanshu.reader.BuildConfig
import com.kanshu.reader.data.db.BookEntity
import com.kanshu.reader.data.prefs.ThemePreferences
import com.kanshu.reader.data.repo.BookRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.UUID

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
            val token = requireToken()
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
            val folderName = resolveFolderName(book.folderId)

            putContent(
                token = token,
                path = path,
                contentBytes = file.readBytes(),
                message = "Add book: ${book.title}"
            )

            upsertBookInCatalog(
                token = token,
                spec = RemoteBookSpec(
                    id = remoteId,
                    title = book.title,
                    author = book.author.ifBlank { "用户上传" },
                    file = remoteFileName,
                    format = book.format.uppercase(),
                    folder = folderName,
                    chapterIndex = book.chapterIndex,
                    scrollOffset = book.scrollOffset,
                    lastReadAt = book.lastReadAt
                )
            )

            // 写作草稿一并备份，更新后仍可编辑
            val draft = bookRepository.resolveDraftFile(book)
            if (draft.exists() && draft.length() > 0L) {
                putContent(
                    token = token,
                    path = "default-books/$remoteId.draft.txt",
                    contentBytes = draft.readBytes(),
                    message = "Add draft: ${book.title}"
                )
            }

            val folderId = bookRepository.ensureFolder(folderName)
            bookRepository.markAsRemote(
                bookId = book.id,
                remoteId = remoteId,
                folderId = folderId
            )

            UploadResult(
                remoteId = remoteId,
                message = "已上传到仓库：${book.title}（$folderName）"
            )
        }
    }

    /**
     * 更新前备份：本地未上传的书先上传，已在仓库的书同步阅读进度。
     */
    suspend fun backupLibrary(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            requireToken()
            val books = bookRepository.getAllBooksOnce()
            if (books.isEmpty()) {
                return@runCatching "书架为空，已跳过备份"
            }
            var uploaded = 0
            var progressed = 0
            val errors = mutableListOf<String>()
            for (book in books) {
                runCatching {
                    if (book.remoteId.isNullOrBlank()) {
                        uploadBook(book).getOrThrow()
                        uploaded++
                    } else {
                        pushProgress(book)
                        progressed++
                    }
                }.onFailure { e ->
                    errors += "${book.title}：${e.message ?: "失败"}"
                }
            }
            buildString {
                append("已备份到远程：新上传 $uploaded 本，进度 $progressed 本")
                if (errors.isNotEmpty()) {
                    append("；失败 ${errors.size}")
                    append("（${errors.take(2).joinToString("；")}）")
                }
            }
        }
    }

    private suspend fun pushProgress(book: BookEntity) {
        val token = requireToken()
        val remoteId = book.remoteId?.takeIf { it.isNotBlank() }
            ?: error("缺少远程 ID")
        val folderName = resolveFolderName(book.folderId)
        mutateCatalog(token, "Sync progress: ${book.title}") { root ->
            ensureFoldersArray(root).also { arr ->
                if (!(0 until arr.length()).any { arr.optString(it) == folderName }) {
                    arr.put(folderName)
                }
            }
            val books = root.optJSONArray("books") ?: JSONArray().also { root.put("books", it) }
            var found = false
            for (i in 0 until books.length()) {
                val item = books.getJSONObject(i)
                if (item.optString("id") == remoteId) {
                    item.put("title", book.title)
                    item.put("chapterIndex", book.chapterIndex)
                    item.put("scrollOffset", book.scrollOffset)
                    item.put("lastReadAt", book.lastReadAt)
                    found = true
                    break
                }
            }
            if (!found) {
                val ext = when {
                    book.format.equals("PDF", true) -> ".pdf"
                    book.format.equals("TXT", true) -> ".txt"
                    else -> ".epub"
                }
                books.put(
                    JSONObject()
                        .put("id", remoteId)
                        .put("title", book.title)
                        .put("author", book.author.ifBlank { "用户上传" })
                        .put("file", "$remoteId$ext")
                        .put("format", book.format.uppercase())
                        .put("folder", folderName)
                        .put("chapterIndex", book.chapterIndex)
                        .put("scrollOffset", book.scrollOffset)
                        .put("lastReadAt", book.lastReadAt)
                )
            }
        }
        val draft = bookRepository.resolveDraftFile(book)
        if (draft.exists() && draft.length() > 0L) {
            putContent(
                token = token,
                path = "default-books/$remoteId.draft.txt",
                contentBytes = draft.readBytes(),
                message = "Update draft: ${book.title}"
            )
        }
    }

    /** 把分类名写入远程 catalog.folders */
    suspend fun ensureRemoteFolder(folderName: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val name = folderName.trim().ifBlank { DEFAULT_REMOTE_FOLDER }
            val token = requireToken()
            mutateCatalog(token, "Add folder: $name") { root ->
                ensureFoldersArray(root).also { arr ->
                    if (!(0 until arr.length()).any { arr.optString(it) == name }) {
                        arr.put(name)
                    }
                }
            }
        }
    }

    /** 更新某本仓库书的远程分类 */
    suspend fun updateRemoteBookFolder(remoteId: String, folderName: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val name = folderName.trim().ifBlank { DEFAULT_REMOTE_FOLDER }
                val token = requireToken()
                mutateCatalog(token, "Move book $remoteId to $name") { root ->
                    ensureFoldersArray(root).also { arr ->
                        if (!(0 until arr.length()).any { arr.optString(it) == name }) {
                            arr.put(name)
                        }
                    }
                    val books = root.optJSONArray("books") ?: JSONArray().also { root.put("books", it) }
                    var found = false
                    for (i in 0 until books.length()) {
                        val item = books.getJSONObject(i)
                        if (item.optString("id") == remoteId) {
                            item.put("folder", name)
                            found = true
                            break
                        }
                    }
                    require(found) { "远程目录里找不到这本书，请先上传到仓库" }
                }
            }
        }

    /**
     * 删除远程分类：从 folders 移除；该书分类改回默认「仓库书」。
     */
    suspend fun removeRemoteFolder(folderName: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val name = folderName.trim()
            require(name.isNotBlank()) { "分类名为空" }
            require(name != DEFAULT_REMOTE_FOLDER) { "默认分类「仓库书」不能删除" }
            val token = requireToken()
            mutateCatalog(token, "Remove folder: $name") { root ->
                val old = root.optJSONArray("folders")
                val next = JSONArray()
                if (old != null) {
                    for (i in 0 until old.length()) {
                        val n = old.optString(i)
                        if (n.isNotBlank() && n != name) next.put(n)
                    }
                }
                if (!(0 until next.length()).any { next.optString(it) == DEFAULT_REMOTE_FOLDER }) {
                    next.put(DEFAULT_REMOTE_FOLDER)
                }
                root.put("folders", next)

                val books = root.optJSONArray("books") ?: return@mutateCatalog
                for (i in 0 until books.length()) {
                    val item = books.getJSONObject(i)
                    if (item.optString("folder") == name) {
                        item.put("folder", DEFAULT_REMOTE_FOLDER)
                    }
                }
            }
        }
    }

    private suspend fun resolveFolderName(folderId: Long?): String {
        if (folderId == null) return DEFAULT_REMOTE_FOLDER
        return bookRepository.getFolder(folderId)?.name?.trim()?.ifBlank { null }
            ?: DEFAULT_REMOTE_FOLDER
    }

    private suspend fun requireToken(): String {
        val token = themePreferences.githubToken.first().trim()
        require(token.isNotEmpty()) {
            "还没配置上传权限。请点右上角设置，填写 GitHub Token 后再同步分类。"
        }
        return token
    }

    private fun upsertBookInCatalog(token: String, spec: RemoteBookSpec) {
        mutateCatalog(token, "Update catalog for ${spec.id}") { root ->
            ensureFoldersArray(root).also { arr ->
                val folder = spec.folder.ifBlank { DEFAULT_REMOTE_FOLDER }
                if (!(0 until arr.length()).any { arr.optString(it) == folder }) {
                    arr.put(folder)
                }
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
                    item.put("folder", spec.folder.ifBlank { DEFAULT_REMOTE_FOLDER })
                    item.put("chapterIndex", spec.chapterIndex)
                    item.put("scrollOffset", spec.scrollOffset)
                    item.put("lastReadAt", spec.lastReadAt)
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
                        .put("folder", spec.folder.ifBlank { DEFAULT_REMOTE_FOLDER })
                        .put("chapterIndex", spec.chapterIndex)
                        .put("scrollOffset", spec.scrollOffset)
                        .put("lastReadAt", spec.lastReadAt)
                )
                root.put("version", root.optInt("version", 1) + 1)
            }
        }
    }

    private fun ensureFoldersArray(root: JSONObject): JSONArray {
        val existing = root.optJSONArray("folders")
        if (existing != null) return existing
        val created = JSONArray().put(DEFAULT_REMOTE_FOLDER)
        root.put("folders", created)
        return created
    }

    private fun mutateCatalog(token: String, message: String, block: (JSONObject) -> Unit) {
        val path = "default-books/catalog.json"
        val existing = getContent(token, path)
        val root = if (existing != null) {
            JSONObject(String(existing.content, StandardCharsets.UTF_8))
        } else {
            JSONObject()
                .put("version", 1)
                .put("folders", JSONArray().put(DEFAULT_REMOTE_FOLDER))
                .put("books", JSONArray())
        }
        block(root)
        if (!root.has("folders")) {
            ensureFoldersArray(root)
        }
        val bytes = root.toString(2).toByteArray(StandardCharsets.UTF_8)
        putContent(
            token = token,
            path = path,
            contentBytes = bytes,
            message = message,
            sha = existing?.sha
        )
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
