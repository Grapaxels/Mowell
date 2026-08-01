package com.grapaxels.mowell.network

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.grapaxels.mowell.MainActivity
import com.grapaxels.mowell.MowellApplication
import com.grapaxels.mowell.R
import com.grapaxels.mowell.auth.AuthRepository
import com.grapaxels.mowell.data.ConversationEntity
import com.grapaxels.mowell.data.MessageEntity
import com.grapaxels.mowell.transport.Route
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Keeps message delivery alive without a third-party push provider. Android
 * requires a visible foreground-service notification for this kind of
 * continuous background connection.
 */
class MessageSyncService : Service() {
    private val serviceJob: Job = SupervisorJob()
    private val scope = CoroutineScope(serviceJob + Dispatchers.IO)
    private lateinit var auth: AuthRepository
    private lateinit var notifier: MessageNotifier

    override fun onCreate() {
        super.onCreate()
        auth = AuthRepository(this)
        notifier = MessageNotifier(this)
        startForeground(SERVICE_NOTIFICATION_ID, serviceNotification())
        scope.launch {
            while (isActive) {
                runCatching { syncMessages() }
                delay(2_000)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (auth.savedSession == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun syncMessages() {
        if (auth.savedSession == null) return
        val dao = (application as MowellApplication).database.dao()
        val remoteConversations = auth.fetchConversations().getOrNull() ?: return
        dao.retainSyncedConversations(remoteConversations.map { it.id }.toSet())
        val hidden = getSharedPreferences("mowell_hidden_messages", Context.MODE_PRIVATE)

        remoteConversations.forEach remoteLoop@ { remote ->
            val existing = dao.getConversation(remote.id)
            dao.upsertConversation(ConversationEntity(
                remote.id, remote.title, existing?.subtitle ?: "Start chatting", remote.isGroup,
                remote.updatedAt, remote.username, remote.avatarUrl, remote.lastSeenAt, remote.members,
                existing?.unreadCount ?: 0, remote.blocked, remote.blockedByMe
            ))
            val fetched = auth.fetchMessages(remote.id, 0L).getOrNull() ?: return@remoteLoop
            val newIncoming = mutableListOf<MessageEntity>()
            fetched.forEach messageLoop@ { item ->
                if (hidden.getStringSet(item.conversationId, emptySet()).orEmpty().contains(item.id)) return@messageLoop
                if (dao.hasMessage(item.id)) return@messageLoop
                val message = MessageEntity(
                    item.id, item.conversationId, if (item.outgoing) "You" else item.sender,
                    item.body, item.sentAt, item.outgoing, Route.INTERNET.name, "sent", item.kind,
                    item.attachmentId, item.attachmentMime, item.attachmentName
                )
                dao.insertMessage(message)
                if (!item.outgoing) newIncoming += message
            }
            fetched.lastOrNull()?.let { last ->
                val current = dao.getConversation(remote.id)
                dao.upsertConversation((current ?: ConversationEntity(remote.id, remote.title, "", remote.isGroup, last.sentAt)).copy(
                    subtitle = preview(last.kind, last.body, last.attachmentName), updatedAt = last.sentAt,
                    blocked = remote.blocked, blockedByMe = remote.blockedByMe
                ))
            }
            if (newIncoming.isNotEmpty()) {
                var unread = 0
                repeat(newIncoming.count { it.kind != "call_end" }) { unread = dao.incrementUnread(remote.id) }
                val latest = newIncoming.last()
                if (latest.kind == "call_end") notifier.clearConversation(remote.id)
                else notifier.show(remote.title, latest, unread.coerceAtLeast(1), remote.avatarUrl)
            }
        }
    }

    private fun preview(kind: String, body: String, attachmentName: String?): String = when (kind) {
        "image" -> "Photo"
        "video" -> "Video"
        "audio" -> "Voice message"
        "file" -> attachmentName ?: "File"
        "location" -> "Location"
        "contact" -> "Contact"
        "call" -> if (runCatching { JSONObject(body).optBoolean("video") }.getOrDefault(false)) "Video call" else "Voice call"
        "call_end" -> "Call ended"
        else -> body
    }

    private fun serviceNotification(): android.app.Notification {
        val channelId = "mowell_message_connection"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Message connection", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Keeps Mowell ready to receive messages"
                setSound(null, null)
                enableVibration(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val open = PendingIntent.getActivity(this, 41002, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_mowell)
            .setContentTitle("Mowell is ready")
            .setContentText("Listening for new messages")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(open)
            .build()
    }

    companion object {
        private const val SERVICE_NOTIFICATION_ID = 41001

        fun start(context: Context) {
            val intent = Intent(context, MessageSyncService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ContextCompat.startForegroundService(context, intent)
            else context.startService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MessageSyncService::class.java))
        }
    }
}

class MowellBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED && AuthRepository(context).savedSession != null) {
            MessageSyncService.start(context)
        }
    }
}
