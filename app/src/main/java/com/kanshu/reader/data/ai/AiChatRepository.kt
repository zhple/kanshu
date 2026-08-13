package com.kanshu.reader.data.ai

import android.content.Context
import com.kanshu.reader.data.db.AiChatDao
import com.kanshu.reader.data.db.AiMessageEntity
import com.kanshu.reader.data.db.AiSessionEntity
import com.kanshu.reader.data.prefs.ThemePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class AiChatRepository(
    private val context: Context,
    private val aiChatDao: AiChatDao,
    private val themePreferences: ThemePreferences
) {
    private val client = DeepSeekClient(
        apiKeyProvider = { themePreferences.deepseekApiKey.first() }
    )

    fun observeSessions(): Flow<List<AiSessionEntity>> = aiChatDao.observeSessions()

    fun observeMessages(sessionId: Long): Flow<List<AiMessageEntity>> =
        aiChatDao.observeMessages(sessionId)

    suspend fun getSession(id: Long): AiSessionEntity? = aiChatDao.getSession(id)

    suspend fun deleteSession(id: Long) = withContext(Dispatchers.IO) {
        aiChatDao.deleteSession(id)
    }

    suspend fun optimizeDraft(
        scenario: String,
        character: String,
        userPersona: String
    ): OptimizedPrompt = withContext(Dispatchers.IO) {
        val optimizer = readAsset("ai/roleplay_prompt_optimizer.txt")
        client.optimizePrompt(
            optimizerSystem = optimizer,
            scenarioDraft = scenario,
            characterDraft = character,
            userPersonaDraft = userPersona
        )
    }

    suspend fun createSession(
        title: String,
        systemPrompt: String,
        openingHint: String
    ): Long = withContext(Dispatchers.IO) {
        val runtime = readAsset("ai/roleplay_runtime.txt")
        val merged = systemPrompt.trim() + "\n\n" + runtime.trim()
        aiChatDao.insertSession(
            AiSessionEntity(
                title = title.trim().ifBlank { "未命名角色聊天" },
                systemPrompt = merged,
                openingHint = openingHint.trim()
            )
        )
    }

    suspend fun listChatMessages(sessionId: Long): List<ChatMessage> =
        withContext(Dispatchers.IO) {
            aiChatDao.listMessages(sessionId)
                .filter { it.role == "user" || it.role == "assistant" }
                .map { ChatMessage(it.role, it.content) }
        }

    suspend fun appendUserMessage(sessionId: Long, content: String): Long =
        withContext(Dispatchers.IO) {
            val id = aiChatDao.insertMessage(
                AiMessageEntity(sessionId = sessionId, role = "user", content = content)
            )
            aiChatDao.touchSession(sessionId)
            id
        }

    suspend fun beginAssistantMessage(sessionId: Long): Long = withContext(Dispatchers.IO) {
        aiChatDao.insertMessage(
            AiMessageEntity(sessionId = sessionId, role = "assistant", content = "")
        )
    }

    suspend fun updateAssistantMessage(messageId: Long, content: String) =
        withContext(Dispatchers.IO) {
            aiChatDao.updateMessageContent(messageId, content)
        }

    suspend fun finishAssistantTurn(sessionId: Long) = withContext(Dispatchers.IO) {
        aiChatDao.touchSession(sessionId)
    }

    suspend fun needsOpening(sessionId: Long): Boolean = withContext(Dispatchers.IO) {
        aiChatDao.assistantCount(sessionId) == 0
    }

    fun streamReply(sessionId: Long, systemPrompt: String, history: List<ChatMessage>) =
        client.chatStream(systemPrompt = systemPrompt, history = history)

    private fun readAsset(path: String): String {
        return context.assets.open(path).bufferedReader().use { it.readText() }
    }
}
