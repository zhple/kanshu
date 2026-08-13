package com.kanshu.reader.data.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 硅基流动文生图。
 * 优先用国内账号普遍可用的模型；Pro 档无权限时自动回退，避免误报成 Key 无效。
 */
class SceneImageClient(
    private val apiKeyProvider: suspend () -> String
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    suspend fun generateToFile(
        prompt: String,
        negative: String,
        seed: Long,
        width: Int,
        height: Int,
        dest: File
    ): File = withContext(Dispatchers.IO) {
        val apiKey = apiKeyProvider().trim()
        require(apiKey.isNotEmpty()) { "请先填写硅基流动 API Key（角色聊天设置里）" }

        dest.parentFile?.mkdirs()
        if (dest.exists()) dest.delete()

        val fullPrompt = buildString {
            append(prompt.trim().take(1800))
            if (negative.isNotBlank()) {
                append(". Avoid: ")
                append(negative.trim().take(400))
            }
        }
        val portrait = height >= width
        val attempts = listOf(
            ModelAttempt(
                model = "black-forest-labs/FLUX.1-dev",
                size = if (portrait) "768x1024" else "1024x768",
                includeSteps = true
            ),
            ModelAttempt(
                model = "Qwen/Qwen-Image",
                size = if (portrait) "928x1664" else "1664x928",
                includeSteps = true
            ),
            ModelAttempt(
                model = "Kwai-Kolors/Kolors",
                size = if (portrait) "768x1024" else "1024x1024",
                includeSteps = true,
                includeNegative = true,
                negative = negative
            )
        )

        var lastError: String? = null
        var sawAuthFailure = false
        for (attempt in attempts) {
            val result = runCatching {
                requestGenerate(
                    apiKey = apiKey,
                    attempt = attempt,
                    prompt = fullPrompt,
                    seed = seed,
                    dest = dest
                )
            }
            if (result.isSuccess) return@withContext dest

            lastError = result.exceptionOrNull()?.message.orEmpty()
            if (isHardAuthFailure(lastError)) {
                sawAuthFailure = true
                // Key 本身无效时换模型也没用
                if (lastError.contains("Invalid", ignoreCase = true) ||
                    lastError.contains("无效")
                ) {
                    error(friendlyUserMessage(lastError, hardAuth = true))
                }
            }
            if (!shouldFallback(lastError)) {
                error(friendlyUserMessage(lastError, hardAuth = sawAuthFailure))
            }
        }
        error(
            friendlyUserMessage(
                lastError.orEmpty().ifBlank { "生图失败" },
                hardAuth = sawAuthFailure
            )
        )
    }

    private data class ModelAttempt(
        val model: String,
        val size: String,
        val includeSteps: Boolean = false,
        val includeNegative: Boolean = false,
        val negative: String = ""
    )

    private fun requestGenerate(
        apiKey: String,
        attempt: ModelAttempt,
        prompt: String,
        seed: Long,
        dest: File
    ) {
        val body = JSONObject()
            .put("model", attempt.model)
            .put("prompt", prompt)
            .put("image_size", attempt.size)
            .put("seed", seed.coerceIn(0, 9_999_999_999L))
            .apply {
                if (attempt.includeSteps) put("num_inference_steps", 20)
                if (attempt.includeNegative && attempt.negative.isNotBlank()) {
                    put("negative_prompt", attempt.negative.trim().take(500))
                }
                // Kolors 支持 batch_size
                if (attempt.model.contains("Kolors")) {
                    put("batch_size", 1)
                    put("guidance_scale", 7.5)
                }
            }
            .toString()
            .toRequestBody(jsonMedia)

        val request = Request.Builder()
            .url("$BASE_URL/images/generations")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error(rawHttpError(response.code, text.take(400)))
            }
            val url = JSONObject(text)
                .optJSONArray("images")
                ?.optJSONObject(0)
                ?.optString("url")
                .orEmpty()
            require(url.isNotBlank()) { "生图返回缺少图片地址" }
            download(url, dest)
        }
    }

    private fun download(url: String, dest: File) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Kanshu-SceneImage")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("下载生图失败 HTTP ${response.code}")
            }
            val bytes = response.body?.bytes() ?: error("生图文件为空")
            require(bytes.size > 1024) { "生图文件异常，请重试" }
            dest.writeBytes(bytes)
        }
    }

    private fun shouldFallback(msg: String): Boolean {
        val m = msg.lowercase()
        return m.contains("20012") ||
            m.contains("does not exist") ||
            m.contains("403") ||
            m.contains("forbidden") ||
            m.contains("permission") ||
            m.contains("not allowed") ||
            m.contains("无权限") ||
            m.contains("not available") ||
            m.contains("overloaded") ||
            m.contains("503")
    }

    private fun isHardAuthFailure(msg: String): Boolean {
        val m = msg.lowercase()
        return m.contains("401") ||
            m.contains("unauthorized") ||
            m.contains("invalid token") ||
            m.contains("invalid api") ||
            m.contains("api key")
    }

    private fun rawHttpError(code: Int, body: String): String {
        return "HTTP $code: $body"
    }

    private fun friendlyUserMessage(raw: String, hardAuth: Boolean): String {
        val m = raw.lowercase()
        return when {
            hardAuth || m.contains("401") || m.contains("invalid token") ->
                "硅基流动 Key 无效，请到设置里重新粘贴正确的 API Key"
            m.contains("403") || m.contains("forbidden") || m.contains("permission") ->
                "当前 Key 无该生图模型权限，请确认硅基账号已开通生图，或换用有余额的账号"
            m.contains("429") -> "生图请求过于频繁，请稍后再试"
            m.contains("余额") || m.contains("balance") || m.contains("insufficient") ->
                "硅基流动余额不足，请先充值"
            else -> "硅基流动生图失败：$raw"
        }
    }

    companion object {
        const val BASE_URL = "https://api.siliconflow.cn/v1"
    }
}
