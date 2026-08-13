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
 * 国内站当前可用高质量档：FLUX-1.1-pro（FLUX.2-pro 多数账号未上架会报 Model does not exist）。
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

        val size = pickImageSize(width, height)
        val fullPrompt = buildString {
            append(prompt.trim().take(1800))
            if (negative.isNotBlank()) {
                append(". Avoid: ")
                append(negative.trim().take(400))
            }
        }

        // 优先高质量；若账号未开通则回退到稳定可用的 FLUX.1-dev
        val models = listOf(MODEL_PRIMARY, MODEL_FALLBACK)
        var lastError: String? = null
        for (model in models) {
            val result = runCatching {
                requestGenerate(
                    apiKey = apiKey,
                    model = model,
                    prompt = fullPrompt,
                    size = size,
                    seed = seed,
                    dest = dest
                )
            }
            if (result.isSuccess) return@withContext dest
            lastError = result.exceptionOrNull()?.message
            val msg = lastError.orEmpty()
            val modelMissing = msg.contains("20012") ||
                msg.contains("does not exist", ignoreCase = true) ||
                msg.contains("Model does not exist", ignoreCase = true)
            if (!modelMissing) {
                error(lastError ?: "生图失败")
            }
        }
        error(lastError ?: "生图失败：当前账号无可用生图模型")
    }

    private fun requestGenerate(
        apiKey: String,
        model: String,
        prompt: String,
        size: String,
        seed: Long,
        dest: File
    ) {
        val body = JSONObject()
            .put("model", model)
            .put("prompt", prompt)
            .put("image_size", size)
            .put("seed", seed.coerceIn(0, 9_999_999_999L))
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
                error(friendlyHttpError(response.code, text.take(300)))
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

    private fun pickImageSize(width: Int, height: Int): String {
        val portrait = height >= width
        return if (portrait) "768x1024" else "1024x768"
    }

    private fun friendlyHttpError(code: Int, body: String): String {
        return when {
            code == 401 || code == 403 -> "硅基流动 API Key 无效或无权限"
            code == 429 -> "生图请求过于频繁，请稍后再试"
            body.contains("20012") || body.contains("does not exist", ignoreCase = true) ->
                "生图模型不可用（$body）"
            else -> "硅基流动生图失败 HTTP $code: $body"
        }
    }

    companion object {
        const val BASE_URL = "https://api.siliconflow.cn/v1"
        /** 国内文档列出的高质量档（比 FLUX.1-dev 更好） */
        const val MODEL_PRIMARY = "black-forest-labs/FLUX-1.1-pro"
        const val MODEL_FALLBACK = "black-forest-labs/FLUX.1-dev"
    }
}
