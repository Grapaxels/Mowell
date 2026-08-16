package com.grapaxels.mowell.network

import android.app.Activity
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import com.grapaxels.mowell.auth.AuthRepository
import com.grapaxels.mowell.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File

data class UpdateInfo(val versionCode: Int, val versionName: String, val apkUrl: String, val required: Boolean, val sha256: String? = null)

class AppUpdater(private val context: Context, private val auth: AuthRepository) {
    private val client = OkHttpClient()

    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            if (auth.serverUrl.contains("example.invalid")) return@withContext null
            val request = Request.Builder()
                .url("${auth.serverUrl}/v1/app/version?check=${System.currentTimeMillis()}")
                .header("Cache-Control", "no-cache, no-store")
                .build()
            val json = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                JSONObject(response.body?.string().orEmpty())
            }
            val code = json.getInt("versionCode")
            val url = json.optString("apkUrl")
            val installed = if (Build.VERSION.SDK_INT >= 28) context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode else @Suppress("DEPRECATION") context.packageManager.getPackageInfo(context.packageName, 0).versionCode.toLong()
            if (code <= installed || url.isBlank() || url == "null") null
            else UpdateInfo(code, json.optString("versionName", code.toString()), url, json.optBoolean("required"), json.optString("sha256").takeIf { it.length == 64 })
        } catch (_: Exception) { null }
    }

    fun downloadAndInstall(activity: Activity, update: UpdateInfo) {
        if (!BuildConfig.SELF_UPDATE) {
            val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${activity.packageName}"))
            runCatching { activity.startActivity(market) }.getOrElse {
                activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${activity.packageName}")))
            }
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !activity.packageManager.canRequestPackageInstalls()) {
            activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}")))
            return
        }
        File(activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "Mowell-update.apk").delete()
        val manager = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(update.apkUrl))
            .setTitle("Mowell ${update.versionName}")
            .setDescription("Downloading secure app update")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(activity, Environment.DIRECTORY_DOWNLOADS, "Mowell-update.apk")
        val id = manager.enqueue(request)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) != id) return
                runCatching { activity.unregisterReceiver(this) }
                val successful = manager.query(DownloadManager.Query().setFilterById(id)).use { cursor -> cursor.moveToFirst() && cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_SUCCESSFUL }
                if (!successful) { Toast.makeText(activity, "Update download failed. Check internet and try again.", Toast.LENGTH_LONG).show(); return }
                val file = File(activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "Mowell-update.apk")
                if (!file.exists()) { Toast.makeText(activity, "Downloaded update file was not found.", Toast.LENGTH_LONG).show(); return }
                if (update.sha256 != null) {
                    val actual = java.security.MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
                    if (!actual.equals(update.sha256, ignoreCase = true)) { file.delete(); Toast.makeText(activity, "Update verification failed. Please download again.", Toast.LENGTH_LONG).show(); return }
                }
                val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.files", file)
                activity.startActivity(Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
        }
        if (Build.VERSION.SDK_INT >= 33) activity.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") activity.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
    }
}
