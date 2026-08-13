package com.kanshu.reader.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY CASE WHEN lastReadAt = 0 THEN addedAt ELSE lastReadAt END DESC")
    fun observeBooks(): Flow<List<BookEntity>>

    @Query(
        """
        SELECT * FROM books
        WHERE (:folderId IS NULL AND folderId IS NULL) OR folderId = :folderId
        ORDER BY CASE WHEN lastReadAt = 0 THEN addedAt ELSE lastReadAt END DESC
        """
    )
    fun observeBooksInFolder(folderId: Long?): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getBook(id: Long): BookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(book: BookEntity): Long

    @Query(
        """
        UPDATE books
        SET chapterIndex = :chapterIndex,
            scrollOffset = :scrollOffset,
            lastReadAt = :lastReadAt
        WHERE id = :id
        """
    )
    suspend fun updateProgress(
        id: Long,
        chapterIndex: Int,
        scrollOffset: Int,
        lastReadAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE books SET folderId = :folderId WHERE id = :bookId")
    suspend fun updateFolder(bookId: Long, folderId: Long?)

    @Query("UPDATE books SET folderId = NULL WHERE folderId = :folderId")
    suspend fun clearFolder(folderId: Long)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM books WHERE folderId = :folderId")
    suspend fun countInFolder(folderId: Long): Int
}
