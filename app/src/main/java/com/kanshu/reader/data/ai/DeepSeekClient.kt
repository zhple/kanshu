package com.kanshu.reader.data.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

data class ChatMessage(
    val role: String,
    val content: String
)

data class OptimizedPrompt(
    val title: String,
    val systemPrompt: String,
    val openingHint: String
)

class DeepSeekClient(
    private val apiKeyProvider: suspend () -> String
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    suspend fun optimizePrompt(
        optimizerSystem: String,
        scenarioDraft: String,
        characterDraft: String,
        userPersonaDraft: String
    ): OptimizedPrompt = withContext(Dispatchers.IO) {
        val userContent = buildString {
            appendLine("【场景想法】")
            appendLine(scenarioDraft.trim())
            appendLine()
            appendLine("【对方角色人设】")
            appendLine(characterDraft.trim())
            appendLine()
            appendLine("【我的人设】")
            appendLine(userPersonaDraft.trim())
            appendLine()
            appendLine("请输出规定 JSON。")
        }
        val raw = chatOnce(
            messages = listOf(
                ChatMessage("system", optimizerSystem),
                ChatMessage("user", userContent)
            ),
            temperature = 0.7
        )
        parseOptimizedPrompt(raw)
    }

    fun chatStream(
        systemPrompt: String,
        history: List<ChatMessage>,
        temperature: Double = 0.9
    ): Flow<String> = flow {
        val apiKey = requireApiKey()
        val messages = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", systemPrompt))
            history.forEach { msg ->
                put(JSONObject().put("role", msg.role).put("content", msg.content))
            }
        }
        val body = JSONObject()
            .put("model", MODEL)
            .put("messages", messages)
            .put("stream", true)
            .put("temperature", temperature)
            .put("thinking", JSONObject().put("type", "disabled"))
            .toString()
            .toRequestBody(jsonMedia)

        val request = Request.Builder()
            .url("$BASE_URL/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val err = response.body?.string().orEmpty().take(300)
                error(friendlyHttpError(response.code, err))
            }
            val source = response.body?.byteStream() ?: error("空响应")
            BufferedReader(InputStreamReader(source, Charsets.UTF_8)).use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isBlank()) continue
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data == "[DONE]") break
                    val delta = runCatching {
                        val json = JSONObject(data)
                        json.getJSONArray("choices")
                            .getJSONObject(0)
                            .optJSONObject("delta")
                            ?.optString("content")
                            .orEmpty()
                    }.getOrDefault("")
                    if (delta.isNotEmpty()) emit(delta)
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun chatOnce(
        messages: List<ChatMessage>,
        temperature: Double
    ): String {
        val apiKey = requireApiKey()
        val arr = JSONArray().apply {
            messages.forEach { msg ->
                put(JSONObject().put("role", msg.role).put("content", msg.content))
            }
        }
        val body = JSONObject()
            .put("model", MODEL)
            .put("messages", arr)
            .put("stream", false)
            .put("temperature", temperature)
            .put("thinking", JSONObject().put("type", "disabled"))
            .toString()
            .toRequestBody(jsonMedia)

        val request = Request.Builder()
            .url("$BASE_URL/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error(friendlyHttpError(response.code, text.take(300)))
            }
            val json = JSONObject(text)
            return json.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .optString("content")
                .trim()
                .ifBlank { error("模型返回为空") }
        }
    }

    private suspend fun requireApiKey(): String {
        val key = apiKeyProvider().trim()
        require(key.isNotEmpty()) { "请先填写 DeepSeek API Key" }
        return key
    }

    private fun friendlyHttpError(code: Int, body: String): String {
        return when (code) {
            401, 403 -> "API Key 无效或无权限，请检查后重试"
            429 -> "请求过于频繁，请稍后再试"
            else -> "DeepSeek 请求失败 HTTP $code: $body"
        }
    }

    private fun parseOptimizedPrompt(raw: String): OptimizedPrompt {
        val jsonText = extractJsonObject(raw)
        val obj = JSONObject(jsonText)
        val title = obj.optString("title").trim().ifBlank { "未命名角色聊天" }
        val systemPrompt = obj.optString("systemPrompt").trim()
        require(systemPrompt.isNotBlank()) { "优化结果缺少 systemPrompt" }
        val openingHint = obj.optString("openingHint").trim().ifBlank {
            "请根据设定，用中文写出富有代入感的第一幕开场（含旁白与情境），不要替用户行动或说话。"
        }
        return OptimizedPrompt(title, systemPrompt, openingHint)
    }

    private fun extractJsonObject(raw: String): String {
        val fenced = Regex("""```(?:json)?\s*([\s\S]*?)```""", RegexOption.IGNORE_CASE)
            .find(raw)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
        if (!fenced.isNullOrBlank() && fenced.startsWith("{")) return fenced
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        require(start >= 0 && end > start) { "无法解析优化结果，请重试" }
        return raw.substring(start, end + 1)
    }

    companion object {
        const val BASE_URL = "https://api.deepseek.com"
        const val MODEL = "deepseek-v4-pro"
    }
}
