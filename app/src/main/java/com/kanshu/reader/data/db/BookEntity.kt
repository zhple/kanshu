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
    val addedAt: Long = System.currentTimeMillis(),
    val chapterIndex: Int = 0,
    val scrollOffset: Int = 0,
    val lastReadAt: Long = 0L
)
