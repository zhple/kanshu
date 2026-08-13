package com.kanshu.reader.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.kanshu.reader.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.min

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

    fun canInstallPackages(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun installPermissionSettingsIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                "package:${context.packageName}".toUri()
            )
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }
    }

    /**
     * 下载 APK 到应用私有目录，完成后可直接调起系统安装界面。
     * [onProgress] 取值 0f..1f；未知总长度时给近似进度。
     */
    suspend fun downloadApk(
        context: Context,
        info: AppUpdateInfo,
        onProgress: (Float) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "updates")
                .also { it.mkdirs() }
            val dest = File(dir, "kanshu-${info.versionName}.apk")
            if (dest.exists()) dest.delete()

            var url = info.apkUrl
            var redirects = 0
            while (redirects < 5) {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    connectTimeout = 20_000
                    readTimeout = 120_000
                    setRequestProperty("User-Agent", "Kanshu-Updater")
                    setRequestProperty("Accept", "*/*")
                }
                val code = conn.responseCode
                if (code in 300..399) {
                    val next = conn.getHeaderField("Location") ?: error("下载跳转失败")
                    url = if (next.startsWith("http")) next else URL(URL(url), next).toString()
                    redirects++
                    continue
                }
                if (code !in 200..299) {
                    error("下载失败：HTTP $code")
                }
                val total = conn.contentLengthLong.takeIf { it > 0 } ?: -1L
                BufferedInputStream(conn.inputStream).use { input ->
                    FileOutputStream(dest).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var readTotal = 0L
                        var lastEmit = -1
                        while (true) {
                            val n = input.read(buffer)
                            if (n <= 0) break
                            output.write(buffer, 0, n)
                            readTotal += n
                            val percent = if (total > 0) {
                                ((readTotal * 100) / total).toInt().coerceIn(0, 100)
                            } else {
                                min(90, 15 + ((readTotal / (256 * 1024)).toInt()))
                            }
                            if (percent != lastEmit) {
                                lastEmit = percent
                                withContext(Dispatchers.Main.immediate) {
                                    onProgress(percent / 100f)
                                }
                            }
                        }
                        output.flush()
                    }
                }
                withContext(Dispatchers.Main.immediate) { onProgress(1f) }
                require(dest.length() > 1024L) { "下载文件异常，请重试" }
                return@runCatching dest
            }
            error("下载跳转过多")
        }
    }

    fun installApk(context: Context, file: File) {
        require(file.exists() && file.length() > 0L) { "安装包不存在" }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_ACTIVITY_NEW_TASK
            )
        }
        // 部分系统需要显式授权给安装器
        context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .forEach { resolve ->
                context.grantUriPermission(
                    resolve.activityInfo.packageName,
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        context.startActivity(intent)
    }

    fun openDownloadPage(context: Context, url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    @Deprecated("改用 downloadApk + installApk")
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
}
