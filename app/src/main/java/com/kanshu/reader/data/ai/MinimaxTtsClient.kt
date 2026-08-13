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
 * MiniMax 同步语音合成 T2A v2（默认 speech-02-turbo）。
 * Key 由调用方提供，不写死在代码里。
 */
class MinimaxTtsClient(
    private val apiKeyProvider: suspend () -> String
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    suspend fun synthesizeToFile(
        text: String,
        voiceId: String,
        dest: File,
        model: String = MODEL_TURBO
    ): File = withContext(Dispatchers.IO) {
        val apiKey = apiKeyProvider().trim()
        require(apiKey.isNotEmpty()) { "请先填写 MiniMax API Key（角色聊天设置里）" }

        val cleaned = sanitizeForSpeech(text)
        require(cleaned.isNotBlank()) { "没有可朗读的文本" }

        dest.parentFile?.mkdirs()
        if (dest.exists()) dest.delete()

        val body = JSONObject()
            .put("model", model)
            .put("text", cleaned.take(MAX_CHARS))
            .put("stream", false)
            .put(
                "voice_setting",
                JSONObject()
                    .put("voice_id", voiceId)
                    .put("speed", 1.0)
                    .put("vol", 1.0)
                    .put("pitch", 0)
            )
            .put(
                "audio_setting",
                JSONObject()
                    .put("sample_rate", 32000)
                    .put("bitrate", 128000)
                    .put("format", "mp3")
                    .put("channel", 1)
            )
            .put("language_boost", "Chinese")
            .put("output_format", "hex")
            .toString()
            .toRequestBody(jsonMedia)

        val request = Request.Builder()
            .url("$BASE_URL/t2a_v2")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error(friendlyHttpError(response.code, raw.take(280)))
            }
            val json = JSONObject(raw)
            val base = json.optJSONObject("base_resp")
            val code = base?.optInt("status_code", 0) ?: 0
            if (code != 0) {
                val msg = base?.optString("status_msg").orEmpty().ifBlank { "合成失败" }
                error(mapApiError(code, msg))
            }
            val audioHex = json.optJSONObject("data")?.optString("audio").orEmpty()
            require(audioHex.isNotBlank()) { "语音返回为空，请换个声线或稍后重试" }
            val bytes = hexToBytes(audioHex)
            require(bytes.size > 64) { "语音文件异常，请重试" }
            dest.writeBytes(bytes)
        }
        dest
    }

    companion object {
        const val BASE_URL = "https://api.minimaxi.com/v1"
        const val MODEL_TURBO = "speech-02-turbo"
        const val MODEL_HD = "speech-02-hd"
        private const val MAX_CHARS = 2500

        fun sanitizeForSpeech(raw: String): String {
            return raw
                .replace(Regex("""```[\s\S]*?```"""), " ")
                .replace(Regex("""!\[[^\]]*]\([^)]*\)"""), " ")
                .replace(Regex("""\[([^\]]*)]\([^)]*\)"""), "$1")
                .replace(Regex("""[*_#>`]{1,3}"""), "")
                .replace(Regex("""\s+"""), " ")
                .trim()
        }

        private fun hexToBytes(hex: String): ByteArray {
            val clean = hex.trim().replace(" ", "")
            require(clean.length % 2 == 0) { "语音数据格式错误" }
            val out = ByteArray(clean.length / 2)
            var i = 0
            while (i < clean.length) {
                out[i / 2] = clean.substring(i, i + 2).toInt(16).toByte()
                i += 2
            }
            return out
        }

        private fun friendlyHttpError(code: Int, body: String): String {
            return when {
                code == 401 || code == 403 -> "MiniMax API Key 无效或无权限，请检查后重试"
                code == 429 -> "语音请求过于频繁，请稍后再试"
                body.contains("1008") || body.contains("insufficient balance", ignoreCase = true) ->
                    "MiniMax 余额不足，请到平台充值后再试"
                else -> "MiniMax 请求失败 HTTP $code: $body"
            }
        }

        private fun mapApiError(code: Int, msg: String): String {
            return when {
                code == 1008 || msg.contains("insufficient balance", ignoreCase = true) ->
                    "MiniMax 余额不足，请到平台充值后再试"
                code == 1004 -> "MiniMax API Key 无效，请重新填写"
                code == 1002 -> "MiniMax 触发限流，请稍后再试"
                else -> "MiniMax 语音失败：$msg（$code）"
            }
        }
    }
}
