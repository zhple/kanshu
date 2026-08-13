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
import kotlin.random.Random

class AiChatRepository(
    private val context: Context,
    private val aiChatDao: AiChatDao,
    private val themePreferences: ThemePreferences
) {
    private val client = DeepSeekClient(
        apiKeyProvider = { themePreferences.deepseekApiKey.first() }
    )
    private val imageClient = SceneImageClient(
        apiKeyProvider = { themePreferences.siliconflowApiKey.first() }
    )
    private val ttsClient = MinimaxTtsClient(
        apiKeyProvider = { themePreferences.minimaxApiKey.first() }
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
        val dna = runCatching {
            client.generateVisualDna(
                dnaSystem = readAsset("ai/roleplay_visual_dna.txt"),
                systemPrompt = merged
            )
        }.getOrNull()
        val preferred = dna?.seed?.takeIf { it > 0 } ?: Random.nextLong(100_000, 999_999_999)
        // 先占位插入拿 sessionId，再用 sessionId 混种，避免不同会话共用同一 seed
        val id = aiChatDao.insertSession(
            AiSessionEntity(
                title = title.trim().ifBlank { "未命名角色聊天" },
                systemPrompt = merged,
                openingHint = openingHint.trim(),
                visualDnaJson = dna?.toJson().orEmpty(),
                imageSeed = 0L
            )
        )
        val sessionSeed = ImageSeeds.sessionBase(id, preferred)
        val dnaJson = dna?.copy(seed = sessionSeed)?.toJson().orEmpty()
        aiChatDao.updateVisualDna(id, dnaJson, sessionSeed)
        id
    }

    suspend fun ensureVisualDna(sessionId: Long): VisualDna = withContext(Dispatchers.IO) {
        val session = aiChatDao.getSession(sessionId) ?: error("会话不存在")
        if (session.visualDnaJson.isNotBlank()) {
            val parsed = VisualDna.parse(session.visualDnaJson)
            val seed = when {
                session.imageSeed > 0 -> ImageSeeds.sessionBase(sessionId, session.imageSeed)
                else -> ImageSeeds.sessionBase(sessionId, parsed.seed)
            }
            if (session.imageSeed != seed || parsed.seed != seed) {
                val fixed = parsed.copy(seed = seed)
                aiChatDao.updateVisualDna(sessionId, fixed.toJson(), seed)
                return@withContext fixed
            }
            return@withContext parsed.copy(seed = seed)
        }
        val dna = client.generateVisualDna(
            dnaSystem = readAsset("ai/roleplay_visual_dna.txt"),
            systemPrompt = session.systemPrompt
        )
        val seed = ImageSeeds.sessionBase(sessionId, dna.seed)
        val fixed = dna.copy(seed = seed)
        aiChatDao.updateVisualDna(sessionId, fixed.toJson(), seed)
        fixed
    }

    /**
     * 为某条 assistant 消息生成场景配图（硅基流动 FLUX.2-pro + 会话/消息级 seed + Visual DNA）。
     * @param force 为 true 时覆盖已有配图
     */
    suspend fun generateSceneImage(
        sessionId: Long,
        messageId: Long,
        force: Boolean = false
    ): String = withContext(Dispatchers.IO) {
        val message = aiChatDao.getMessage(messageId) ?: error("消息不存在")
        require(message.sessionId == sessionId) { "消息不属于该会话" }
        require(message.role == "assistant") { "只能为 AI 回复配图" }
        if (!force && message.imagePath.isNotBlank() && File(message.imagePath).exists()) {
            return@withContext message.imagePath
        }

        val dna = ensureVisualDna(sessionId)
        val focus = message.content.trim().take(900)
        val recent = aiChatDao.listMessages(sessionId)
            .filter { it.role == "user" || it.role == "assistant" }
            .takeLast(4)
            .joinToString("\n") { "${it.role}: ${it.content.take(280)}" }

        val dialogueBlock = buildString {
            appendLine("【本条要画的画面（唯一焦点，messageId=$messageId）】")
            appendLine(focus.ifBlank { "开场画面" })
            appendLine()
            appendLine("【前后文】")
            appendLine(recent)
            appendLine()
            appendLine("请画出与本条焦点明显对应的独特构图，不要复用其它对话的默认站姿空镜。")
        }

        val spec = client.buildSceneImagePrompt(
            sceneSystem = readAsset("ai/roleplay_scene_image.txt"),
            dna = dna,
            recentDialogue = dialogueBlock
        )

        val promptFingerprint = (spec.prompt + "|m$messageId|s$sessionId").hashCode()
        val seedBase = ImageSeeds.forMessage(dna.seed, messageId, promptFingerprint)
        val seed = if (force) {
            ImageSeeds.forMessage(seedBase, messageId, Random.nextInt())
        } else {
            seedBase
        }

        val dest = File(
            context.filesDir,
            "ai_images/s${sessionId}_m${messageId}.jpg"
        )
        if (dest.exists()) dest.delete()

        imageClient.generateToFile(
            prompt = spec.prompt,
            negative = dna.negative,
            seed = seed,
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

    suspend fun hasMinimaxKey(): Boolean =
        themePreferences.minimaxApiKey.first().isNotBlank()

    suspend fun currentTtsVoiceId(): String =
        themePreferences.minimaxVoiceId.first()

    suspend fun setTtsVoiceId(voiceId: String) {
        themePreferences.setMinimaxVoiceId(voiceId)
    }

    /**
     * 合成并缓存本条消息的朗读音频，返回本地 mp3 路径。
     * @param force 强制重新合成（换声线后使用）
     */
    suspend fun synthesizeSpeech(
        sessionId: Long,
        messageId: Long,
        text: String,
        force: Boolean = false
    ): String = withContext(Dispatchers.IO) {
        val voiceId = themePreferences.minimaxVoiceId.first()
        val dest = File(
            context.filesDir,
            "ai_tts/s${sessionId}_m${messageId}_${voiceId.hashCode()}.mp3"
        )
        if (!force && dest.exists() && dest.length() > 64) {
            return@withContext dest.absolutePath
        }
        ttsClient.synthesizeToFile(
            text = text,
            voiceId = voiceId,
            dest = dest
        )
        dest.absolutePath
    }

    fun streamReply(sessionId: Long, systemPrompt: String, history: List<ChatMessage>) =
        client.chatStream(systemPrompt = systemPrompt, history = history)

    private fun readAsset(path: String): String {
        return context.assets.open(path).bufferedReader().use { it.readText() }
    }
}
