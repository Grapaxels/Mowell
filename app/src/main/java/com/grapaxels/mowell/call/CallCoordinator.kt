package com.grapaxels.mowell.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.grapaxels.mowell.CallSession
import com.grapaxels.mowell.transport.InternetTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.jitsi.meet.sdk.BroadcastEvent
import org.jitsi.meet.sdk.BroadcastIntentHelper
import org.jitsi.meet.sdk.JitsiMeetActivity
import org.jitsi.meet.sdk.JitsiMeetConferenceOptions
import org.json.JSONObject
import java.net.URL
import java.util.UUID

object CallCoordinator {
    private const val PREFS = "mowell_active_call"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var registered = false

    fun initialize(context: Context) {
        if (registered) return
        registered = true
        val app = context.applicationContext
        val filter = IntentFilter().apply {
            addAction(BroadcastEvent.Type.CONFERENCE_TERMINATED.action)
            addAction(BroadcastEvent.Type.PARTICIPANT_LEFT.action)
        }
        LocalBroadcastManager.getInstance(app).registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val room = prefs.getString("room", null) ?: return
                val group = prefs.getBoolean("group", false)
                if (intent.action == BroadcastEvent.Type.PARTICIPANT_LEFT.action && !group) {
                    hangUp(app)
                }
                if (intent.action == BroadcastEvent.Type.CONFERENCE_TERMINATED.action) {
                    val conversationId = prefs.getString("conversation", null)
                    if (!conversationId.isNullOrBlank()) {
                        scope.launch {
                            val payload = JSONObject().put("clientId", UUID.randomUUID().toString())
                                .put("kind", "call_end").put("body", JSONObject().put("room", room).toString()).toString()
                            InternetTransport(app).send(conversationId, payload)
                        }
                    }
                    prefs.edit().clear().apply()
                }
            }
        }, filter)
    }

    fun launch(context: Context, session: CallSession) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("room", session.room).putString("conversation", session.conversationId)
            .putBoolean("group", session.group).apply()
        val options = JitsiMeetConferenceOptions.Builder()
            .setServerURL(URL("https://meet.jit.si"))
            .setRoom(session.room)
            .setAudioMuted(false)
            .setVideoMuted(!session.video)
            .setAudioOnly(!session.video)
            .setFeatureFlag("welcomepage.enabled", false)
            .setFeatureFlag("prejoinpage.enabled", false)
            .setFeatureFlag("toolbox.enabled", true)
            .build()
        JitsiMeetActivity.launch(context, options)
    }

    fun endIfActive(context: Context, room: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString("room", null) == room) hangUp(context)
    }

    private fun hangUp(context: Context) {
        LocalBroadcastManager.getInstance(context.applicationContext)
            .sendBroadcast(BroadcastIntentHelper.buildHangUpIntent())
    }
}
