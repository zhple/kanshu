package com.kanshu.reader.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ai_messages",
    foreignKeys = [
        ForeignKey(
            entity = AiSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId")]
)
data class AiMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    /** user / assistant / system */
    val role: String,
    val content: String,
    /** 本地场景图绝对路径，空表示未生成 */
    val imagePath: String = "",
    val imagePrompt: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
