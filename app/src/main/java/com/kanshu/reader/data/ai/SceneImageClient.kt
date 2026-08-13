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
 * 硅基流动文生图（默认 FLUX.1-dev）。
 * seed 参与采样；不同会话/消息必须传入不同 seed，否则易撞图。
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
        val body = JSONObject()
            .put("model", MODEL)
            .put("prompt", prompt.trim().take(2000))
            .put("image_size", size)
            .put("seed", seed.coerceIn(0, 9_999_999_999L))
            .put("num_inference_steps", 20)
            .apply {
                if (negative.isNotBlank()) put("negative_prompt", negative.trim().take(500))
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
        dest
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
        return when (code) {
            401, 403 -> "硅基流动 API Key 无效或无权限"
            429 -> "生图请求过于频繁，请稍后再试"
            else -> "硅基流动生图失败 HTTP $code: $body"
        }
    }

    companion object {
        const val BASE_URL = "https://api.siliconflow.cn/v1"
        const val MODEL = "black-forest-labs/FLUX.1-dev"
    }
}
