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
import androidx.core.content.FileProvider
import com.grapaxels.mowell.auth.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File

data class UpdateInfo(val versionCode: Int, val versionName: String, val apkUrl: String, val required: Boolean)

class AppUpdater(private val context: Context, private val auth: AuthRepository) {
    private val client = OkHttpClient()

    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            if (auth.serverUrl.contains("example.invalid")) return@withContext null
            val response = client.newCall(Request.Builder().url("${auth.serverUrl}/v1/app/version").build()).execute()
            if (!response.isSuccessful) return@withContext null
            val json = JSONObject(response.body?.string().orEmpty())
            val code = json.getInt("versionCode")
            val url = json.optString("apkUrl")
            if (code <= 3 || url.isBlank() || url == "null") null
            else UpdateInfo(code, json.optString("versionName", code.toString()), url, json.optBoolean("required"))
        } catch (_: Exception) { null }
    }

    fun downloadAndInstall(activity: Activity, update: UpdateInfo) {
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
                val file = File(activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "Mowell-update.apk")
                if (!file.exists()) return
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
