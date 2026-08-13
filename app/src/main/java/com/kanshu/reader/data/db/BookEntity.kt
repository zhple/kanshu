package com.kanshu.reader.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val author: String,
    val format: String,
    val fileName: String,
    val folderId: Long? = null,
    /** LOCAL = 用户上传；REMOTE = 仓库默认书 */
    val source: String = SOURCE_LOCAL,
    /** 远程书稳定 ID，用于更新时去重同步 */
    val remoteId: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
    val chapterIndex: Int = 0,
    val scrollOffset: Int = 0,
    val lastReadAt: Long = 0L
) {
    val isRemote: Boolean get() = source == SOURCE_REMOTE

    companion object {
        const val SOURCE_LOCAL = "LOCAL"
        const val SOURCE_REMOTE = "REMOTE"
    }
}
