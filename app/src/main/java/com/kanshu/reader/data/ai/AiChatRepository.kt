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
import java.io.File

class AiChatRepository(
    private val context: Context,
    private val aiChatDao: AiChatDao,
    private val themePreferences: ThemePreferences
) {
    private val client = DeepSeekClient(
        apiKeyProvider = { themePreferences.deepseekApiKey.first() }
    )
    private val imageClient = SceneImageClient()

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
        val dna = runCatching {
            client.generateVisualDna(
                dnaSystem = readAsset("ai/roleplay_visual_dna.txt"),
                systemPrompt = merged
            )
        }.getOrNull()
        aiChatDao.insertSession(
            AiSessionEntity(
                title = title.trim().ifBlank { "未命名角色聊天" },
                systemPrompt = merged,
                openingHint = openingHint.trim(),
                visualDnaJson = dna?.toJson().orEmpty(),
                imageSeed = dna?.seed ?: 0L
            )
        )
    }

    suspend fun ensureVisualDna(sessionId: Long): VisualDna = withContext(Dispatchers.IO) {
        val session = aiChatDao.getSession(sessionId) ?: error("会话不存在")
        if (session.visualDnaJson.isNotBlank()) {
            return@withContext VisualDna.parse(session.visualDnaJson).let { parsed ->
                if (session.imageSeed > 0 && parsed.seed != session.imageSeed) {
                    parsed.copy(seed = session.imageSeed)
                } else {
                    parsed
                }
            }
        }
        val dna = client.generateVisualDna(
            dnaSystem = readAsset("ai/roleplay_visual_dna.txt"),
            systemPrompt = session.systemPrompt
        )
        aiChatDao.updateVisualDna(sessionId, dna.toJson(), dna.seed)
        dna
    }

    /**
     * 为某条 assistant 消息生成场景配图（免费 Pollinations + 固定 seed + Visual DNA）。
     * @return 本地图片路径
     */
    suspend fun generateSceneImage(sessionId: Long, messageId: Long): String =
        withContext(Dispatchers.IO) {
            val message = aiChatDao.getMessage(messageId) ?: error("消息不存在")
            require(message.sessionId == sessionId) { "消息不属于该会话" }
            require(message.role == "assistant") { "只能为 AI 回复配图" }
            if (message.imagePath.isNotBlank() && File(message.imagePath).exists()) {
                return@withContext message.imagePath
            }

            val dna = ensureVisualDna(sessionId)
            val recent = aiChatDao.listMessages(sessionId)
                .filter { it.role == "user" || it.role == "assistant" }
                .takeLast(6)
                .joinToString("\n") { "${it.role}: ${it.content.take(400)}" }

            val spec = client.buildSceneImagePrompt(
                sceneSystem = readAsset("ai/roleplay_scene_image.txt"),
                dna = dna,
                recentDialogue = recent
            )

            val dest = File(
                context.filesDir,
                "ai_images/s${sessionId}_m${messageId}.jpg"
            )
            imageClient.generateToFile(
                prompt = spec.prompt,
                negative = dna.negative,
                seed = dna.seed,
                width = spec.width,
                height = spec.height,
                dest = dest
            )
            aiChatDao.updateMessageImage(messageId, dest.absolutePath, spec.prompt)
            aiChatDao.touchSession(sessionId)
            dest.absolutePath
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
