package com.grapaxels.mowell.call

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat

/** Lightweight linked-device scanner using the installed Android System WebView. */
class MowellQrScannerActivity : ComponentActivity() {
    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 811)
        } else loadScanner()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun loadScanner() {
        if (::webView.isInitialized) return
        webView = WebView(this).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
            settings.javaScriptEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mediaPlaybackRequiresUserGesture = false
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?) = true
            }
            webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest) = runOnUiThread {
                    val allowed = request.resources.filter { it == PermissionRequest.RESOURCE_VIDEO_CAPTURE }.toTypedArray()
                    request.grant(allowed)
                }
            }
            addJavascriptInterface(ScannerBridge(), "MowellScanner")
        }
        setContentView(webView)
        val html = assets.open("mowell_qr_scanner.html").bufferedReader().use { it.readText() }
        webView.loadDataWithBaseURL("https://mowellweb.grapaxels.in/", html, "text/html", "UTF-8", null)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 811) loadScanner()
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.evaluateJavascript("window.stopScanner && window.stopScanner()", null)
            webView.removeJavascriptInterface("MowellScanner")
            webView.destroy()
        }
        super.onDestroy()
    }

    private inner class ScannerBridge {
        @JavascriptInterface fun found(payload: String) = runOnUiThread {
            if (payload.isBlank() || isFinishing) return@runOnUiThread
            setResult(Activity.RESULT_OK, Intent().putExtra(RESULT_PAYLOAD, payload.trim()))
            finish()
        }
    }

    companion object { const val RESULT_PAYLOAD = "mowell_link_payload" }
}
