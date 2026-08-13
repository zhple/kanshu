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

    fun observeBooks(): Flow<List<BookEntity>> = bookDao.observeBooks()

    fun observeBooksInFolder(folderId: Long?): Flow<List<BookEntity>> =
        bookDao.observeBooksInFolder(folderId)

    fun observeFolders(): Flow<List<FolderEntity>> = folderDao.observeFolders()

    suspend fun getBook(id: Long): BookEntity? = bookDao.getBook(id)

    suspend fun getFolder(id: Long): FolderEntity? = folderDao.getFolder(id)

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
                        folderId = folderId
                    )
                )
            }
        }

    suspend fun createFolder(name: String): Long = withContext(Dispatchers.IO) {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "文件夹名称不能为空" }
        folderDao.insert(FolderEntity(name = trimmed))
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

    suspend fun moveBook(bookId: Long, folderId: Long?) = withContext(Dispatchers.IO) {
        bookDao.updateFolder(bookId, folderId)
    }

    suspend fun deleteBook(book: BookEntity) = withContext(Dispatchers.IO) {
        File(booksDir, book.fileName).delete()
        bookDao.delete(book.id)
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
