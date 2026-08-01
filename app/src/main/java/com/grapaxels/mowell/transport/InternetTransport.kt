package com.grapaxels.mowell.transport

import android.content.Context
import com.grapaxels.mowell.auth.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/** Vercel-compatible REST delivery. Incoming messages are synchronized separately. */
class InternetTransport(context: Context) : MessageTransport {
    private val auth = AuthRepository(context)
    private val client = OkHttpClient()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    override suspend fun send(peer: String?, payload: String): Boolean = withContext(Dispatchers.IO) {
        val session = auth.savedSession ?: return@withContext false
        if (auth.serverUrl.contains("example.invalid") || peer.isNullOrBlank()) return@withContext false
        runCatching {
            val clientId = payload.substringBefore('|')
            val body = payload.substringAfter('|', payload)
            val json = JSONObject().put("clientId", clientId).put("body", body).put("kind", "text")
            client.newCall(Request.Builder().url("${auth.serverUrl}/v1/conversations/$peer/messages")
                .header("Authorization", "Bearer ${session.token}")
                .post(json.toString().toRequestBody(jsonType)).build()).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }
}
