package com.grapaxels.mowell.call

import android.Manifest
import android.app.NotificationManager
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.app.ActivityCompat
import com.grapaxels.mowell.auth.AuthRepository
import org.json.JSONObject
import java.lang.ref.WeakReference

/** Lightweight in-app WebRTC calling backed by Android System WebView. */
class MowellCallActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private lateinit var room: String

    companion object {
        private var current = WeakReference<MowellCallActivity>(null)
        fun endRoom(room: String) {
            current.get()?.takeIf { it.room == room }?.let { activity ->
                activity.runOnUiThread { activity.remoteEnded() }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        room = intent.getStringExtra("room").orEmpty()
        val conversation = intent.getStringExtra("conversation").orEmpty()
        val auth = AuthRepository(this).savedSession
        if (room.isBlank() || conversation.isBlank() || auth == null) {
            finish()
            return
        }
        current = WeakReference(this)
        intent.getIntExtra("notification_id", 0).takeIf { it != 0 }?.let {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(it)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audio.mode = AudioManager.MODE_IN_COMMUNICATION
        audio.isSpeakerphoneOn = intent.getBooleanExtra("video", false)

        webView = WebView(this).apply {
            setBackgroundColor(android.graphics.Color.rgb(17, 17, 24))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = false
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mediaPlaybackRequiresUserGesture = false
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?) = true
            }
            webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest) {
                    runOnUiThread {
                        val allowed = request.resources.filter {
                            it == PermissionRequest.RESOURCE_AUDIO_CAPTURE || it == PermissionRequest.RESOURCE_VIDEO_CAPTURE
                        }.toTypedArray()
                        request.grant(allowed)
                    }
                }
            }
            addJavascriptInterface(CallBridge(audio), "MowellNative")
        }
        setContentView(webView)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                webView.evaluateJavascript("window.mowellHangup && window.mowellHangup()", null)
                webView.postDelayed({ finish() }, 250)
            }
        })
        val permissions = arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
        if (permissions.any { ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }) {
            ActivityCompat.requestPermissions(this, permissions, 701)
        }
        loadCallPage(auth.token, conversation)
    }

    private fun loadCallPage(token: String, conversation: String) {
        val config = JSONObject()
            .put("api", AuthRepository(this).serverUrl)
            .put("token", token)
            .put("conversation", conversation)
            .put("room", room)
            .put("name", intent.getStringExtra("name") ?: "Mowell call")
            .put("avatar", intent.getStringExtra("avatar"))
            .put("userId", AuthRepository(this).savedSession?.user?.id.orEmpty())
            .put("video", intent.getBooleanExtra("video", false))
            .put("initiator", intent.getBooleanExtra("initiator", false))
            .put("group", intent.getBooleanExtra("group", false))
        val html = assets.open("mowell_call.html").bufferedReader().use { it.readText() }
            .replace("__MOWELL_CONFIG__", config.toString().replace("</", "<\\/"))
        webView.loadDataWithBaseURL("https://mowell-api.grapaxels.in/", html, "text/html", "UTF-8", null)
    }

    private fun remoteEnded() {
        if (::webView.isInitialized) webView.evaluateJavascript("window.mowellRemoteEnded && window.mowellRemoteEnded()", null)
        webView.postDelayed({ finish() }, 500)
    }

    override fun onDestroy() {
        if (current.get() === this) current.clear()
        if (::webView.isInitialized) {
            webView.evaluateJavascript("window.mowellClose && window.mowellClose()", null)
            webView.removeJavascriptInterface("MowellNative")
            webView.destroy()
        }
        val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audio.mode = AudioManager.MODE_NORMAL
        audio.isSpeakerphoneOn = false
        super.onDestroy()
    }

    private inner class CallBridge(private val audio: AudioManager) {
        @JavascriptInterface fun setSpeaker(enabled: Boolean) = runOnUiThread { audio.isSpeakerphoneOn = enabled }
        @JavascriptInterface fun finishCall() = runOnUiThread { finish() }
    }
}
