package com.grapaxels.mowell

import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

class MowellMapActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val latitude = intent.getDoubleExtra("latitude", 0.0)
        val longitude = intent.getDoubleExtra("longitude", 0.0)
        setContent {
            val holder = remember { arrayOfNulls<WebView>(1) }
            DisposableEffect(Unit) { onDispose { holder[0]?.destroy() } }
            Box(Modifier.fillMaxSize().background(Color(0xFFF7F5F0))) {
                AndroidView(modifier = Modifier.fillMaxSize(), factory = { context ->
                    WebView(context).apply {
                        holder[0] = this
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.loadsImagesAutomatically = true
                        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        webViewClient = WebViewClient()
                        loadUrl(mapUrl(latitude, longitude))
                    }
                })
                IconButton(onClick = { finish() }, Modifier.align(Alignment.TopStart).padding(18.dp).clip(CircleShape).background(Color.White)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Color(0xFF15131A))
                }
            }
        }
    }
}

fun mapUrl(latitude: Double, longitude: Double): String {
    return "https://maps.google.com/maps?q=$latitude,$longitude&z=16&output=embed"
}

fun mapHtml(latitude: Double, longitude: Double, interactive: Boolean): String = """
<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"><style>html,body,#map{height:100%;width:100%;margin:0;background:#ede8ff}.leaflet-control-attribution{font-size:9px}</style></head>
<body><div id="map"></div><script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script><script>
const map=L.map('map',{zoomControl:${if (interactive) "true" else "false"},dragging:${if (interactive) "true" else "false"},scrollWheelZoom:${if (interactive) "true" else "false"},doubleClickZoom:${if (interactive) "true" else "false"}}).setView([$latitude,$longitude],16);
L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'&copy; OpenStreetMap'}).addTo(map);L.marker([$latitude,$longitude]).addTo(map);
</script></body></html>
""".trimIndent()
