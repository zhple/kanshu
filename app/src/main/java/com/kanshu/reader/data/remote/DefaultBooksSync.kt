package com.kanshu.reader.data.remote

import android.content.Context
import com.kanshu.reader.BuildConfig
import com.kanshu.reader.data.repo.BookRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class DefaultBooksSync(
    private val context: Context,
    private val bookRepository: BookRepository
) {
    data class SyncResult(
        val added: Int = 0,
        val skipped: Int = 0,
        val failed: Int = 0,
        val reassigned: Int = 0,
        val message: String = ""
    )

    suspend fun sync(): SyncResult = withContext(Dispatchers.IO) {
        val catalog = loadCatalog()
            ?: return@withContext SyncResult(message = "无法读取默认书目录")

        // 先确保远程分类文件夹存在
        for (folderName in catalog.folders) {
            bookRepository.ensureFolder(folderName)
        }
        bookRepository.ensureFolder(DEFAULT_REMOTE_FOLDER)

        var added = 0
        var skipped = 0
        var failed = 0
        var moved = 0

        for (spec in catalog.books) {
            val folderName = spec.folder.ifBlank { DEFAULT_REMOTE_FOLDER }
            val folderId = bookRepository.ensureFolder(folderName)
            val existing = bookRepository.getByRemoteId(spec.id)
            if (existing != null) {
                if (existing.folderId != folderId) {
                    bookRepository.moveBook(existing.id, folderId)
                    moved++
                }
                skipped++
                continue
            }
            val ok = runCatching {
                val destName = "remote_${spec.id}${extensionOf(spec.file, spec.format)}"
                val dest = bookRepository.booksFile(destName)
                if (!copyFromAssets(spec.file, dest)) {
                    downloadRemoteFile(spec.file, dest)
                }
                require(dest.exists() && dest.length() > 0L) { "文件为空" }
                bookRepository.insertRemoteBook(
                    remoteId = spec.id,
                    title = spec.title,
                    author = spec.author,
                    format = spec.format,
                    fileName = destName,
                    folderId = folderId
                )
            }.isSuccess
            if (ok) added++ else failed++
        }

        SyncResult(
            added = added,
            skipped = skipped,
            failed = failed,
            reassigned = moved,
            message = when {
                added > 0 && moved > 0 -> "已同步 $added 本仓库书，并更新 $moved 本分类"
                added > 0 -> "已同步 $added 本仓库书"
                moved > 0 -> "已更新 $moved 本仓库书的分类"
                failed > 0 -> "有 $failed 本仓库书同步失败"
                else -> "仓库书已是最新"
            }
        )
    }

    private fun loadCatalog(): RemoteCatalog? {
        val remote = runCatching {
            val url = BuildConfig.DEFAULT_BOOKS_CATALOG_URL
            if (url.isBlank()) return@runCatching null
            httpGet(url)
        }.getOrNull()
        if (!remote.isNullOrBlank()) {
            return runCatching { CatalogParser.parse(remote) }.getOrNull()
        }
        val local = runCatching {
            context.assets.open("default_books/catalog.json").bufferedReader().use { it.readText() }
        }.getOrNull() ?: return null
        return runCatching { CatalogParser.parse(local) }.getOrNull()
    }

    private fun copyFromAssets(fileName: String, dest: File): Boolean {
        return runCatching {
            context.assets.open("default_books/$fileName").use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            dest.exists() && dest.length() > 0L
        }.getOrDefault(false)
    }

    private fun downloadRemoteFile(fileName: String, dest: File) {
        val base = BuildConfig.DEFAULT_BOOKS_BASE_URL.trimEnd('/')
        require(base.isNotBlank()) { "未配置远程书地址" }
        val url = "$base/$fileName"
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Kanshu-DefaultBooks")
        }
        if (conn.responseCode !in 200..299) {
            error("下载失败 HTTP ${conn.responseCode}")
        }
        conn.inputStream.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
    }

    private fun httpGet(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", "Kanshu-DefaultBooks")
            setRequestProperty("Accept", "application/json")
        }
        if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
        return conn.inputStream.bufferedReader().use { it.readText() }
    }

    private fun extensionOf(file: String, format: String): String {
        val fromFile = file.substringAfterLast('.', missingDelimiterValue = "")
        if (fromFile.isNotBlank()) return ".${fromFile.lowercase()}"
        return when (format.uppercase()) {
            "PDF" -> ".pdf"
            "TXT" -> ".txt"
            else -> ".epub"
        }
    }
}
