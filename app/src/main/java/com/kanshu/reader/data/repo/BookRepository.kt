package com.kanshu.reader.data.repo

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.kanshu.reader.data.db.BookDao
import com.kanshu.reader.data.db.BookEntity
import com.kanshu.reader.data.db.FolderDao
import com.kanshu.reader.data.db.FolderEntity
import com.kanshu.reader.reader.BookFormat
import com.kanshu.reader.reader.BookParser
import com.kanshu.reader.reader.TextPdfExporter
import com.kanshu.reader.reader.WriteMarkers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class BookRepository(
    private val bookDao: BookDao,
    private val folderDao: FolderDao,
    private val filesDir: File
) {
    private val booksDir: File
        get() = File(filesDir, "books").also { if (!it.exists()) it.mkdirs() }

    private val writeAssetsDir: File
        get() = File(booksDir, "write_assets").also { if (!it.exists()) it.mkdirs() }

    fun booksFile(name: String): File = File(booksDir, name)

    fun observeBooks(): Flow<List<BookEntity>> = bookDao.observeBooks()

    fun observeBooksInFolder(folderId: Long?): Flow<List<BookEntity>> =
        bookDao.observeBooksInFolder(folderId)

    fun observeFolders(): Flow<List<FolderEntity>> = folderDao.observeFolders()

    suspend fun getBook(id: Long): BookEntity? = bookDao.getBook(id)

    suspend fun getAllBooksOnce(): List<BookEntity> = withContext(Dispatchers.IO) {
        bookDao.getAllOnce()
    }

    suspend fun getFolder(id: Long): FolderEntity? = folderDao.getFolder(id)

    suspend fun getByRemoteId(remoteId: String): BookEntity? = bookDao.getByRemoteId(remoteId)

    suspend fun ensureFolder(name: String): Long = withContext(Dispatchers.IO) {
        folderDao.getAllOnce().firstOrNull { it.name == name }?.id
            ?: folderDao.insert(FolderEntity(name = name))
    }

    suspend fun importBook(
        contentResolver: ContentResolver,
        uri: Uri,
        folderId: Long? = null
    ): Result<Long> =
        withContext(Dispatchers.IO) {
            runCatching {
                val displayName = queryDisplayName(contentResolver, uri) ?: "book"
                val format = BookFormat.fromFileName(displayName)
                    ?: error("仅支持 TXT、EPUB 或 PDF 文件")

                val safeName = UUID.randomUUID().toString() + format.extension
                val dest = File(booksDir, safeName)
                contentResolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                } ?: error("无法读取所选文件")

                val meta = BookParser.readMetadata(dest, format, displayName)
                bookDao.insert(
                    BookEntity(
                        title = meta.title,
                        author = meta.author,
                        format = format.name,
                        fileName = safeName,
                        folderId = folderId,
                        source = BookEntity.SOURCE_LOCAL,
                        remoteId = null
                    )
                )
            }
        }

    suspend fun insertRemoteBook(
        remoteId: String,
        title: String,
        author: String,
        format: String,
        fileName: String,
        folderId: Long?
    ): Long = withContext(Dispatchers.IO) {
        bookDao.insert(
            BookEntity(
                title = title,
                author = author,
                format = format.uppercase(),
                fileName = fileName,
                folderId = folderId,
                source = BookEntity.SOURCE_REMOTE,
                remoteId = remoteId
            )
        )
    }

    suspend fun createFolder(name: String): Long = withContext(Dispatchers.IO) {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "文件夹名称不能为空" }
        folderDao.getAllOnce().firstOrNull { it.name == trimmed }?.id
            ?: folderDao.insert(FolderEntity(name = trimmed))
    }

    suspend fun renameFolder(id: Long, name: String) = withContext(Dispatchers.IO) {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "文件夹名称不能为空" }
        folderDao.rename(id, trimmed)
    }

    suspend fun deleteFolder(id: Long) = withContext(Dispatchers.IO) {
        bookDao.clearFolder(id)
        folderDao.delete(id)
    }

    enum class WriteSaveFormat { TXT, PDF }

    suspend fun createWrittenBook(
        title: String,
        content: String,
        format: WriteSaveFormat,
        folderId: Long? = null,
        author: String = "我写的"
    ): Long = withContext(Dispatchers.IO) {
        val trimmedTitle = title.trim().ifBlank { "未命名文稿" }
        require(content.isNotBlank()) { "内容不能为空" }
        val idBase = UUID.randomUUID().toString()
        when (format) {
            WriteSaveFormat.TXT -> {
                val safeName = "$idBase.txt"
                val dest = File(booksDir, safeName)
                writeUtf8Text(dest, WriteMarkers.stripImagesForPlainText(content))
                writeUtf8Text(draftFileFor(safeName), content)
                bookDao.insert(
                    BookEntity(
                        title = trimmedTitle,
                        author = author.trim().ifBlank { "我写的" },
                        format = BookFormat.TXT.name,
                        fileName = safeName,
                        folderId = folderId,
                        source = BookEntity.SOURCE_LOCAL,
                        remoteId = null
                    )
                )
            }
            WriteSaveFormat.PDF -> {
                val safeName = "$idBase.pdf"
                writeUtf8Text(draftFileFor(safeName), content)
                TextPdfExporter.export(
                    title = trimmedTitle,
                    content = content,
                    dest = File(booksDir, safeName)
                ) { path -> resolveWriteImage(path) }
                bookDao.insert(
                    BookEntity(
                        title = trimmedTitle,
                        author = author.trim().ifBlank { "我写的" },
                        format = BookFormat.PDF.name,
                        fileName = safeName,
                        folderId = folderId,
                        source = BookEntity.SOURCE_LOCAL,
                        remoteId = null
                    )
                )
            }
        }
    }

    suspend fun createTextBook(
        title: String,
        content: String,
        folderId: Long? = null,
        author: String = "我写的"
    ): Long = createWrittenBook(title, content, WriteSaveFormat.TXT, folderId, author)

    suspend fun updateWrittenBook(
        bookId: Long,
        title: String,
        content: String,
        format: WriteSaveFormat
    ) = withContext(Dispatchers.IO) {
        val book = bookDao.getBook(bookId) ?: error("文稿不存在")
        require(content.isNotBlank()) { "内容不能为空" }
        val trimmedTitle = title.trim().ifBlank { "未命名文稿" }
        val oldFile = File(booksDir, book.fileName)
        val oldDraft = draftFileFor(book.fileName)

        when (format) {
            WriteSaveFormat.TXT -> {
                val newName = if (book.fileName.endsWith(".txt", true)) {
                    book.fileName
                } else {
                    UUID.randomUUID().toString() + ".txt"
                }
                val dest = File(booksDir, newName)
                writeUtf8Text(dest, WriteMarkers.stripImagesForPlainText(content))
                writeUtf8Text(draftFileFor(newName), content)
                if (newName != book.fileName) {
                    if (oldFile.exists()) oldFile.delete()
                    if (oldDraft.exists()) oldDraft.delete()
                    bookDao.updateFile(bookId, newName, BookFormat.TXT.name)
                }
                bookDao.rename(bookId, trimmedTitle)
            }
            WriteSaveFormat.PDF -> {
                val newName = if (book.fileName.endsWith(".pdf", true)) {
                    book.fileName
                } else {
                    UUID.randomUUID().toString() + ".pdf"
                }
                val dest = File(booksDir, newName)
                writeUtf8Text(draftFileFor(newName), content)
                TextPdfExporter.export(
                    title = trimmedTitle,
                    content = content,
                    dest = dest
                ) { path -> resolveWriteImage(path) }
                if (newName != book.fileName) {
                    if (oldFile.exists()) oldFile.delete()
                    if (oldDraft.exists()) oldDraft.delete()
                    bookDao.updateFile(bookId, newName, BookFormat.PDF.name)
                }
                bookDao.rename(bookId, trimmedTitle)
            }
        }
    }

    suspend fun updateTextBook(
        bookId: Long,
        title: String,
        content: String
    ) = updateWrittenBook(bookId, title, content, WriteSaveFormat.TXT)

    suspend fun readTextContent(bookId: Long): String = withContext(Dispatchers.IO) {
        val book = bookDao.getBook(bookId) ?: error("文稿不存在")
        val draft = draftFileFor(book.fileName)
        if (draft.exists()) {
            return@withContext draft.readText(Charsets.UTF_8).removePrefix("\uFEFF")
        }
        require(
            book.format.equals("TXT", ignoreCase = true) ||
                book.format.equals("PDF", ignoreCase = true)
        ) { "只能编辑 TXT/PDF 文稿" }
        val file = File(booksDir, book.fileName)
        require(file.exists()) { "文件不存在" }
        if (book.format.equals("PDF", ignoreCase = true)) {
            error("该 PDF 没有可编辑草稿，请重新创建文稿")
        }
        file.readText(Charsets.UTF_8).removePrefix("\uFEFF")
    }

    suspend fun canEditBook(bookId: Long): Boolean = withContext(Dispatchers.IO) {
        val book = bookDao.getBook(bookId) ?: return@withContext false
        when {
            book.format.equals("TXT", ignoreCase = true) -> true
            book.format.equals("PDF", ignoreCase = true) -> draftFileFor(book.fileName).exists()
            else -> false
        }
    }

    suspend fun importWriteImage(
        contentResolver: ContentResolver,
        uri: Uri
    ): String = withContext(Dispatchers.IO) {
        val ext = guessImageExtension(contentResolver, uri)
        val name = "img_${UUID.randomUUID()}$ext"
        val dest = File(writeAssetsDir, name)
        contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } ?: error("无法读取图片")
        require(dest.exists() && dest.length() > 0L) { "图片保存失败" }
        "write_assets/$name"
    }

    fun resolveWriteImage(path: String): File? {
        val trimmed = path.trim()
        if (trimmed.isEmpty()) return null
        val asFile = File(trimmed)
        if (asFile.isAbsolute && asFile.exists()) return asFile
        val underBooks = File(booksDir, trimmed)
        if (underBooks.exists()) return underBooks
        val underAssets = File(writeAssetsDir, trimmed.substringAfterLast('/'))
        return underAssets.takeIf { it.exists() }
    }

    fun writeAssetFile(fileName: String): File = File(writeAssetsDir, fileName)

    suspend fun renameBook(id: Long, title: String) = withContext(Dispatchers.IO) {
        val trimmed = title.trim()
        require(trimmed.isNotEmpty()) { "书名不能为空" }
        bookDao.rename(id, trimmed)
    }

    suspend fun moveBook(bookId: Long, folderId: Long?) = withContext(Dispatchers.IO) {
        bookDao.updateFolder(bookId, folderId)
    }

    suspend fun deleteBook(book: BookEntity) = withContext(Dispatchers.IO) {
        File(booksDir, book.fileName).delete()
        draftFileFor(book.fileName).delete()
        bookDao.delete(book.id)
    }

    suspend fun markAsRemote(bookId: Long, remoteId: String, folderId: Long?) =
        withContext(Dispatchers.IO) {
            bookDao.markRemote(
                bookId = bookId,
                remoteId = remoteId,
                folderId = folderId,
                source = BookEntity.SOURCE_REMOTE
            )
        }

    suspend fun updateProgress(
        id: Long,
        chapterIndex: Int,
        scrollOffset: Int,
        lastReadAt: Long = System.currentTimeMillis()
    ) {
        bookDao.updateProgress(id, chapterIndex, scrollOffset, lastReadAt)
    }

    fun resolveFile(book: BookEntity): File = File(booksDir, book.fileName)

    fun resolveDraftFile(book: BookEntity): File = draftFileFor(book.fileName)

    private fun draftFileFor(bookFileName: String): File {
        val base = bookFileName
            .removeSuffix(".txt").removeSuffix(".TXT")
            .removeSuffix(".pdf").removeSuffix(".PDF")
        return File(booksDir, "$base.draft.txt")
    }

    private fun writeUtf8Text(file: File, content: String) {
        file.outputStream().use { out ->
            out.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
            out.write(content.toByteArray(Charsets.UTF_8))
        }
    }

    private fun guessImageExtension(contentResolver: ContentResolver, uri: Uri): String {
        val name = queryDisplayName(contentResolver, uri)?.lowercase().orEmpty()
        return when {
            name.endsWith(".png") -> ".png"
            name.endsWith(".webp") -> ".webp"
            name.endsWith(".gif") -> ".gif"
            name.endsWith(".jpeg") || name.endsWith(".jpg") -> ".jpg"
            else -> {
                val type = contentResolver.getType(uri)?.lowercase().orEmpty()
                when {
                    type.contains("png") -> ".png"
                    type.contains("webp") -> ".webp"
                    type.contains("gif") -> ".gif"
                    else -> ".jpg"
                }
            }
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
