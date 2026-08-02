package com.grapaxels.mowell.network

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.grapaxels.mowell.auth.AuthRepository
import com.grapaxels.mowell.transport.InternetTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_REPLY -> {
                        val reply = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_REPLY)?.toString()?.trim().orEmpty()
                        val conversation = intent.getStringExtra(EXTRA_CONVERSATION).orEmpty()
                        if (reply.isNotBlank() && conversation.isNotBlank()) {
                            val payload = JSONObject().put("clientId", UUID.randomUUID().toString()).put("body", reply).put("kind", "text").toString()
                            InternetTransport(app).send(conversation, payload)
                        }
                    }
                    ACTION_DECLINE, ACTION_DECLINE_WITH_REPLY -> {
                        val room = intent.getStringExtra(EXTRA_ROOM).orEmpty()
                        val auth = AuthRepository(app)
                        val token = auth.savedSession?.token
                        if (room.isNotBlank() && token != null) {
                            val body = JSONObject().put("type", "leave").put("payload", JSONObject().put("reason", "declined")).toString()
                            OkHttpClient().newCall(Request.Builder().url("${auth.serverUrl}/v1/calls/$room/signals")
                                .header("Authorization", "Bearer $token").post(body.toRequestBody("application/json".toMediaType())).build()).execute().close()
                        }
                        val reply = if (intent.action == ACTION_DECLINE_WITH_REPLY) {
                            RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_REPLY)?.toString()?.trim()
                                ?: intent.getStringExtra(EXTRA_REPLY_TEXT)?.trim()
                        } else null
                        val conversation = intent.getStringExtra(EXTRA_CONVERSATION).orEmpty()
                        if (!reply.isNullOrBlank() && conversation.isNotBlank()) {
                            val payload = JSONObject().put("clientId", UUID.randomUUID().toString()).put("body", reply).put("kind", "text").toString()
                            InternetTransport(app).send(conversation, payload)
                        }
                    }
                    ACTION_ACCEPT_CONNECTION, ACTION_DECLINE_CONNECTION -> {
                        val requestId = intent.getStringExtra(EXTRA_REQUEST).orEmpty()
                        if (requestId.isNotBlank()) AuthRepository(app).respondConnectionRequest(requestId, intent.action == ACTION_ACCEPT_CONNECTION)
                    }
                    ACTION_ACCEPT_GROUP, ACTION_DECLINE_GROUP -> {
                        val invitationId = intent.getStringExtra(EXTRA_INVITATION).orEmpty()
                        if (invitationId.isNotBlank()) AuthRepository(app).respondGroupInvitation(invitationId, intent.action == ACTION_ACCEPT_GROUP)
                    }
                }
                intent.getIntExtra(EXTRA_NOTIFICATION, 0).takeIf { it != 0 }?.let {
                    (app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(it)
                }
            } finally { pending.finish() }
        }
    }

    companion object {
        const val ACTION_REPLY = "com.grapaxels.mowell.REPLY"
        const val ACTION_DECLINE = "com.grapaxels.mowell.DECLINE_CALL"
        const val ACTION_DECLINE_WITH_REPLY = "com.grapaxels.mowell.DECLINE_CALL_WITH_REPLY"
        const val ACTION_ACCEPT_CONNECTION = "com.grapaxels.mowell.ACCEPT_CONNECTION"
        const val ACTION_DECLINE_CONNECTION = "com.grapaxels.mowell.DECLINE_CONNECTION"
        const val ACTION_ACCEPT_GROUP = "com.grapaxels.mowell.ACCEPT_GROUP"
        const val ACTION_DECLINE_GROUP = "com.grapaxels.mowell.DECLINE_GROUP"
        const val KEY_REPLY = "mowell_reply"
        const val EXTRA_CONVERSATION = "conversation"
        const val EXTRA_ROOM = "room"
        const val EXTRA_NOTIFICATION = "notification_id"
        const val EXTRA_REPLY_TEXT = "reply_text"
        const val EXTRA_REQUEST = "connection_request_id"
        const val EXTRA_INVITATION = "group_invitation_id"
    }
}
