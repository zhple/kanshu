package com.kanshu.reader.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AiChatDao {
    @Query("SELECT * FROM ai_sessions ORDER BY updatedAt DESC")
    fun observeSessions(): Flow<List<AiSessionEntity>>

    @Query("SELECT * FROM ai_sessions WHERE id = :id")
    suspend fun getSession(id: Long): AiSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: AiSessionEntity): Long

    @Query("UPDATE ai_sessions SET title = :title, systemPrompt = :systemPrompt, openingHint = :openingHint, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateSessionMeta(
        id: Long,
        title: String,
        systemPrompt: String,
        openingHint: String,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query(
        "UPDATE ai_sessions SET visualDnaJson = :visualDnaJson, imageSeed = :imageSeed, updatedAt = :updatedAt WHERE id = :id"
    )
    suspend fun updateVisualDna(
        id: Long,
        visualDnaJson: String,
        imageSeed: Long,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE ai_sessions SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun touchSession(id: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM ai_sessions WHERE id = :id")
    suspend fun deleteSession(id: Long)

    @Query("SELECT * FROM ai_messages WHERE sessionId = :sessionId ORDER BY id ASC")
    fun observeMessages(sessionId: Long): Flow<List<AiMessageEntity>>

    @Query("SELECT * FROM ai_messages WHERE sessionId = :sessionId ORDER BY id ASC")
    suspend fun listMessages(sessionId: Long): List<AiMessageEntity>

    @Query("SELECT * FROM ai_messages WHERE id = :id")
    suspend fun getMessage(id: Long): AiMessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: AiMessageEntity): Long

    @Query("UPDATE ai_messages SET content = :content WHERE id = :id")
    suspend fun updateMessageContent(id: Long, content: String)

    @Query("UPDATE ai_messages SET imagePath = :imagePath, imagePrompt = :imagePrompt WHERE id = :id")
    suspend fun updateMessageImage(id: Long, imagePath: String, imagePrompt: String)

    @Query("SELECT COUNT(*) FROM ai_messages WHERE sessionId = :sessionId AND role = 'assistant'")
    suspend fun assistantCount(sessionId: Long): Int
}
