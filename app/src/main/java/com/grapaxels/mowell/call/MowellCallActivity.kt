package com.grapaxels.mowell.call

import android.Manifest
import android.app.NotificationManager
import android.app.AlertDialog
import android.app.PictureInPictureParams
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Build
import android.util.Rational
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.grapaxels.mowell.auth.AuthRepository
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.lang.ref.WeakReference

/** Lightweight in-app WebRTC calling backed by Android System WebView. */
class MowellCallActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private lateinit var room: String
    private lateinit var conversationId: String
    private lateinit var authToken: String
    private var callPageLoaded = false
    private var videoActive = false
    private var cameraPermissionRequestInFlight = false
    private var cameraStartDelayMs = 0L

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
        conversationId = intent.getStringExtra("conversation").orEmpty()
        videoActive = intent.getBooleanExtra("video", false)
        val auth = AuthRepository(this).savedSession
        if (room.isBlank() || conversationId.isBlank() || auth == null) {
            finish()
            return
        }
        authToken = auth.token
        val previousCall = current.get()?.takeIf { it !== this && !it.isFinishing }
        if (previousCall != null) {
            cameraStartDelayMs = 700L
            previousCall.runOnUiThread { previousCall.releaseForReplacement() }
        }
        current = WeakReference(this)
        intent.getIntExtra("notification_id", 0).takeIf { it != 0 }?.let {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(it)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audio.mode = AudioManager.MODE_IN_COMMUNICATION
        audio.isSpeakerphoneOn = intent.getBooleanExtra("video", false)
        val darkMode = getSharedPreferences("mowell_ui", Context.MODE_PRIVATE).getBoolean("dark_mode", false)

        webView = WebView(this).apply {
            setBackgroundColor(if (darkMode) android.graphics.Color.rgb(15, 15, 16) else android.graphics.Color.WHITE)
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
                            when (it) {
                                PermissionRequest.RESOURCE_AUDIO_CAPTURE -> ActivityCompat.checkSelfPermission(
                                    this@MowellCallActivity, Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                PermissionRequest.RESOURCE_VIDEO_CAPTURE -> ActivityCompat.checkSelfPermission(
                                    this@MowellCallActivity, Manifest.permission.CAMERA
                                ) == PackageManager.PERMISSION_GRANTED
                                else -> false
                            }
                        }.toTypedArray()
                        if (allowed.isNotEmpty()) request.grant(allowed) else request.deny()
                        if (request.resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE) &&
                            ActivityCompat.checkSelfPermission(this@MowellCallActivity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
                        ) requestCameraPermissionFromWeb()
                    }
                }
            }
            addJavascriptInterface(CallBridge(audio), "MowellNative")
        }
        setContentView(webView)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!enterCallPictureInPicture()) moveTaskToBack(true)
            }
        })
        if (intent.getBooleanExtra("initiator", false)) {
            lifecycleScope.launch {
                AuthRepository(this@MowellCallActivity).ringCall(room, conversationId, videoActive)
                    .onSuccess { requestCallPermissions() }
                    .onFailure { showCallStartError(it.message ?: "Could not start call") }
            }
        } else requestCallPermissions()
    }

    private fun requestCallPermissions() {
        val permissions = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (videoActive) add(Manifest.permission.CAMERA)
        }.toTypedArray()
        if (permissions.any { ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }) {
            ActivityCompat.requestPermissions(this, permissions, 701)
        } else loadCallPage(authToken, conversationId)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 702) {
            cameraPermissionRequestInFlight = false
            val granted = ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            if (::webView.isInitialized) {
                webView.evaluateJavascript("window.mowellCameraPermissionChanged && window.mowellCameraPermissionChanged($granted)", null)
            }
            if (!granted && !isFinishing) {
                AlertDialog.Builder(this).setTitle("Camera permission required")
                    .setMessage("Allow camera access in Android Settings, then return to the call and tap the camera button.")
                    .setPositiveButton("OK", null).show()
            }
            return
        }
        if (requestCode != 701) return
        if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            loadCallPage(authToken, conversationId)
        } else {
            AlertDialog.Builder(this).setTitle("Call permission required")
                .setMessage("Allow microphone access${if (videoActive) " and camera access" else ""} to use this call.")
                .setPositiveButton("Close") { _, _ -> finish() }.setOnCancelListener { finish() }.show()
        }
    }

    private fun showCallStartError(message: String) {
        AlertDialog.Builder(this).setTitle("Mowell call").setMessage(message)
            .setPositiveButton("Close") { _, _ -> finish() }.setOnCancelListener { finish() }.show()
    }

    private fun loadCallPage(token: String, conversation: String) {
        if (callPageLoaded) return
        callPageLoaded = true
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
            .put("darkMode", getSharedPreferences("mowell_ui", Context.MODE_PRIVATE).getBoolean("dark_mode", false))
        val html = assets.open("mowell_call.html").bufferedReader().use { it.readText() }
            .replace("__MOWELL_CONFIG__", config.toString().replace("</", "<\\/"))
        webView.postDelayed({
            if (!isFinishing && !isDestroyed) {
                webView.loadDataWithBaseURL("https://mowell-api.grapaxels.in/", html, "text/html", "UTF-8", null)
            }
        }, cameraStartDelayMs)
    }

    private fun requestCameraPermissionFromWeb() {
        if (cameraPermissionRequestInFlight || isFinishing) return
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            webView.evaluateJavascript("window.mowellCameraPermissionChanged && window.mowellCameraPermissionChanged(true)", null)
            return
        }
        cameraPermissionRequestInFlight = true
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 702)
    }

    private fun releaseForReplacement() {
        if (::webView.isInitialized) webView.evaluateJavascript("window.mowellClose && window.mowellClose()", null)
        finish()
    }

    /** Keep a live call visible when the user leaves the Mowell activity. */
    private fun enterCallPictureInPicture(): Boolean {
        // Audio calls return to the app normally. PiP is reserved for real video,
        // avoiding a floating window full of call controls or an avatar placeholder.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !callPageLoaded || !videoActive || isFinishing || isInPictureInPictureMode) return false
        return runCatching {
            val builder = PictureInPictureParams.Builder().setAspectRatio(Rational(9, 16))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) builder.setSeamlessResizeEnabled(true)
            enterPictureInPictureMode(builder.build())
        }.getOrDefault(false)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        enterCallPictureInPicture()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (::webView.isInitialized) webView.evaluateJavascript("window.mowellPip && window.mowellPip($isInPictureInPictureMode)", null)
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
        @JavascriptInterface fun setVideoActive(enabled: Boolean) = runOnUiThread { videoActive = enabled }
        @JavascriptInterface fun setSpeaker(enabled: Boolean) = runOnUiThread { audio.isSpeakerphoneOn = enabled }
        @JavascriptInterface fun hasCameraPermission(): Boolean =
            ActivityCompat.checkSelfPermission(this@MowellCallActivity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        @JavascriptInterface fun requestCameraPermission() = runOnUiThread { requestCameraPermissionFromWeb() }
        @JavascriptInterface fun finishCall() = runOnUiThread { finish() }
        @JavascriptInterface fun showMessage(message: String) = runOnUiThread {
            if (!isFinishing) AlertDialog.Builder(this@MowellCallActivity).setTitle("Mowell call").setMessage(message).setPositiveButton("OK", null).show()
        }
        @JavascriptInterface fun playBusyTone() = runOnUiThread {
            val tone = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 75)
            tone.startTone(ToneGenerator.TONE_SUP_BUSY, 1600)
            webView.postDelayed({ tone.release() }, 1800)
        }
    }
}
