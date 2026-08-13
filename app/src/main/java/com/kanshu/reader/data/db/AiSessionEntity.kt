package com.kanshu.reader.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ai_sessions")
data class AiSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val systemPrompt: String,
    val openingHint: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
