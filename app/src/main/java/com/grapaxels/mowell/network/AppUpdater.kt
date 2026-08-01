package com.grapaxels.mowell.network

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import com.grapaxels.mowell.auth.AuthRepository
import com.grapaxels.mowell.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

data class UpdateInfo(val versionCode: Int, val versionName: String, val apkUrl: String, val required: Boolean, val sha256: String? = null)

class AppUpdater(private val context: Context, private val auth: AuthRepository) {
    private val client = OkHttpClient()
    private val state = context.getSharedPreferences("mowell_update_state", Context.MODE_PRIVATE)

    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            if (auth.serverUrl.contains("example.invalid")) return@withContext null
            val response = client.newCall(Request.Builder().url("${auth.serverUrl}/v1/app/version").build()).execute()
            if (!response.isSuccessful) return@withContext null
            val json = JSONObject(response.body?.string().orEmpty())
            val code = json.getInt("versionCode")
            val url = json.optString("apkUrl")
            val installed = if (Build.VERSION.SDK_INT >= 28) context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode else @Suppress("DEPRECATION") context.packageManager.getPackageInfo(context.packageName, 0).versionCode.toLong()
            if (code <= installed || url.isBlank() || url == "null") null
            else UpdateInfo(code, json.optString("versionName", code.toString()), url, json.optBoolean("required"), json.optString("sha256").takeIf { it.length == 64 })
        } catch (_: Exception) { null }
    }

    suspend fun download(update: UpdateInfo, onProgress: (Int) -> Unit): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            if (!BuildConfig.SELF_UPDATE) error("This build receives updates through Google Play")
            val downloadDirectory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: error("Phone storage is unavailable")
            downloadDirectory.mkdirs()
            val updateFile = File(downloadDirectory, "Mowell-update.apk")
            val temporaryFile = File(downloadDirectory, "Mowell-update.part")
            state.edit().remove("pending_install").apply()
            temporaryFile.delete()

            val request = Request.Builder()
                .url(update.apkUrl)
                .header("User-Agent", "Mowell-Android/${BuildConfig.VERSION_NAME}")
                .header("Accept", "application/vnd.android.package-archive, application/octet-stream")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Download server returned HTTP ${response.code}")
                val body = response.body ?: error("Download server returned an empty file")
                val total = body.contentLength()
                val digest = MessageDigest.getInstance("SHA-256")
                body.byteStream().use { input ->
                    FileOutputStream(temporaryFile).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var downloaded = 0L
                        var lastProgress = -1
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            digest.update(buffer, 0, count)
                            downloaded += count
                            val progress = if (total > 0) ((downloaded * 100L) / total).toInt().coerceIn(0, 100) else -1
                            if (progress != lastProgress) { lastProgress = progress; onProgress(progress) }
                        }
                        output.fd.sync()
                    }
                }
                if (temporaryFile.length() < 100_000L) error("The download was not an APK file")
                val zipHeader = temporaryFile.inputStream().use { input -> byteArrayOf(input.read().toByte(), input.read().toByte()) }
                if (zipHeader[0] != 'P'.code.toByte() || zipHeader[1] != 'K'.code.toByte()) error("The server returned a web page instead of the APK")
                val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
                if (update.sha256 != null && !actualHash.equals(update.sha256, ignoreCase = true)) {
                    error("Security check failed: the APK checksum does not match")
                }
            }

            updateFile.delete()
            if (!temporaryFile.renameTo(updateFile)) {
                temporaryFile.copyTo(updateFile, overwrite = true)
                temporaryFile.delete()
            }
            state.edit().putBoolean("pending_install", true).apply()
            onProgress(100)
            updateFile
        }.onFailure { File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "Mowell-update.part").delete() }
    }

    fun install(activity: Activity, updateFile: File) {
        if (!BuildConfig.SELF_UPDATE) {
            val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${activity.packageName}"))
            runCatching { activity.startActivity(market) }.getOrElse {
                activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${activity.packageName}")))
            }
            return
        }
        openInstallerOrPermission(activity, updateFile)
    }

    fun resumePendingInstall(activity: Activity) {
        if (!BuildConfig.SELF_UPDATE || !state.getBoolean("pending_install", false)) return
        val file = File(activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "Mowell-update.apk")
        if (!file.exists()) { state.edit().remove("pending_install").apply(); return }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || activity.packageManager.canRequestPackageInstalls()) {
            openInstallerOrPermission(activity, file)
        }
    }

    private fun openInstallerOrPermission(activity: Activity, file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !activity.packageManager.canRequestPackageInstalls()) {
            activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}")))
            return
        }
        state.edit().remove("pending_install").apply()
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.files", file)
        activity.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}
