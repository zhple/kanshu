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

    fun booksFile(name: String): File = File(booksDir, name)

    fun observeBooks(): Flow<List<BookEntity>> = bookDao.observeBooks()

    fun observeBooksInFolder(folderId: Long?): Flow<List<BookEntity>> =
        bookDao.observeBooksInFolder(folderId)

    fun observeFolders(): Flow<List<FolderEntity>> = folderDao.observeFolders()

    suspend fun getBook(id: Long): BookEntity? = bookDao.getBook(id)

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

    suspend fun createTextBook(
        title: String,
        content: String,
        folderId: Long? = null,
        author: String = "我写的"
    ): Long = withContext(Dispatchers.IO) {
        val trimmedTitle = title.trim().ifBlank { "未命名文稿" }
        require(content.isNotBlank()) { "内容不能为空" }
        val safeName = UUID.randomUUID().toString() + ".txt"
        val dest = File(booksDir, safeName)
        dest.writeText(content, Charsets.UTF_8)
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

    suspend fun updateTextBook(
        bookId: Long,
        title: String,
        content: String
    ) = withContext(Dispatchers.IO) {
        val book = bookDao.getBook(bookId) ?: error("文稿不存在")
        require(book.format.equals("TXT", ignoreCase = true)) { "只能编辑 TXT 文稿" }
        require(content.isNotBlank()) { "内容不能为空" }
        val trimmedTitle = title.trim().ifBlank { "未命名文稿" }
        File(booksDir, book.fileName).writeText(content, Charsets.UTF_8)
        bookDao.rename(bookId, trimmedTitle)
    }

    suspend fun readTextContent(bookId: Long): String = withContext(Dispatchers.IO) {
        val book = bookDao.getBook(bookId) ?: error("文稿不存在")
        require(book.format.equals("TXT", ignoreCase = true)) { "只能打开 TXT 文稿" }
        val file = File(booksDir, book.fileName)
        require(file.exists()) { "文件不存在" }
        file.readText(Charsets.UTF_8)
    }

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

    suspend fun updateProgress(id: Long, chapterIndex: Int, scrollOffset: Int) {
        bookDao.updateProgress(id, chapterIndex, scrollOffset)
    }

    fun resolveFile(book: BookEntity): File = File(booksDir, book.fileName)

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
