package com.kanshu.reader.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.kanshu.reader.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class AppUpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val releaseNotes: String,
    val apkUrl: String,
    val htmlUrl: String
)

object UpdateChecker {
    suspend fun check(): Result<AppUpdateInfo?> = withContext(Dispatchers.IO) {
        runCatching {
            val api = BuildConfig.UPDATE_API_URL
            if (api.isBlank()) return@runCatching null

            val conn = (URL(api).openConnection() as HttpURLConnection).apply {
                connectTimeout = 12_000
                readTimeout = 12_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Kanshu-Updater")
            }
            if (conn.responseCode !in 200..299) {
                error("检查更新失败：HTTP ${conn.responseCode}")
            }
            val body = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
            val json = JSONObject(body)
            val tag = json.optString("tag_name").removePrefix("v")
            val versionCode = parseVersionCode(tag, json.optString("name"))
            if (versionCode <= BuildConfig.VERSION_CODE) return@runCatching null

            val assets = json.optJSONArray("assets") ?: return@runCatching null
            var apkUrl = ""
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name")
                if (name.endsWith(".apk", ignoreCase = true)) {
                    apkUrl = asset.optString("browser_download_url")
                    break
                }
            }
            if (apkUrl.isBlank()) return@runCatching null

            AppUpdateInfo(
                versionCode = versionCode,
                versionName = tag.ifBlank { versionCode.toString() },
                releaseNotes = json.optString("body").orEmpty(),
                apkUrl = apkUrl,
                htmlUrl = json.optString("html_url")
            )
        }
    }

    private fun parseVersionCode(tag: String, name: String): Int {
        Regex("""(?:versionCode|code)[=:]?\s*(\d+)""", RegexOption.IGNORE_CASE)
            .find("$name $tag")
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?.let { return it }

        // 支持标签：v2 或 v1.1.0+2（+ 后为 versionCode）
        Regex("""(?:^v?)(\d+)$""", RegexOption.IGNORE_CASE)
            .find(tag.trim())
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?.let { return it }

        Regex("""\+(\d+)$""")
            .find(tag.trim())
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?.let { return it }

        return 0
    }

    fun openDownload(context: Context, url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun enqueueApkDownload(context: Context, info: AppUpdateInfo): Long {
        val request = DownloadManager.Request(Uri.parse(info.apkUrl))
            .setTitle("看书更新 ${info.versionName}")
            .setDescription("正在下载新版本")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(
                context,
                Environment.DIRECTORY_DOWNLOADS,
                "kanshu-${info.versionName}.apk"
            )
            .setMimeType("application/vnd.android.package-archive")
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return dm.enqueue(request)
    }

    fun installDownloadedApk(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
