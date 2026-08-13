package com.kanshu.reader.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ai_sessions")
data class AiSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val systemPrompt: String,
    val openingHint: String = "",
    /** Visual DNA JSON：外貌锁 + 画风；会话内不变 */
    val visualDnaJson: String = "",
    /** 文生图固定 seed，有助于风格稳定（免费） */
    val imageSeed: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
