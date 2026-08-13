package com.kanshu.reader.data.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * 免费文生图（Pollinations）。seed 免费且有助于风格稳定；
 * 角色一致性主要靠 prompt 里反复粘贴 Visual DNA 的 lock。
 */
class SceneImageClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun generateToFile(
        prompt: String,
        negative: String,
        seed: Long,
        width: Int,
        height: Int,
        dest: File
    ): File = withContext(Dispatchers.IO) {
        dest.parentFile?.mkdirs()
        if (dest.exists()) dest.delete()

        val fullPrompt = buildString {
            append(prompt.trim())
            if (negative.isNotBlank()) {
                append(". Avoid: ")
                append(negative.trim())
            }
        }
        val encoded = URLEncoder.encode(fullPrompt, StandardCharsets.UTF_8.name())
            .replace("+", "%20")
        val url =
            "https://image.pollinations.ai/prompt/$encoded" +
                "?width=${width.coerceIn(512, 1024)}" +
                "&height=${height.coerceIn(512, 1280)}" +
                "&seed=$seed" +
                "&nologo=true" +
                "&enhance=true"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Kanshu-SceneImage")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("生图失败 HTTP ${response.code}（免费服务可能繁忙，请稍后重试）")
            }
            val bytes = response.body?.bytes() ?: error("生图返回空")
            require(bytes.size > 1024) { "生图文件异常，请重试" }
            dest.writeBytes(bytes)
        }
        dest
    }
}
