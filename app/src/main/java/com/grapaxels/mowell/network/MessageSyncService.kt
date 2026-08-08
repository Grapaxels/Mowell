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
import com.grapaxels.mowell.call.CallCoordinator
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
    private lateinit var updater: AppUpdater
    private var nextUpdateCheckAt = 0L
    private var nextSocialCheckAt = 0L
    private var nextFullMessageSyncAt = 0L

    override fun onCreate() {
        super.onCreate()
        auth = AuthRepository(this)
        notifier = MessageNotifier(this)
        updater = AppUpdater(this, auth)
        startForeground(SERVICE_NOTIFICATION_ID, serviceNotification())
        scope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                if (now >= nextFullMessageSyncAt) {
                    runCatching { syncMessages() }
                    nextFullMessageSyncAt = System.currentTimeMillis() + 15_000L
                }
                // This request waits on the Mowell API and returns as soon as a
                // message changes, normally within one 250 ms backend check.
                runCatching { syncMessageChanges() }
                if (System.currentTimeMillis() >= nextSocialCheckAt) {
                    runCatching { syncApprovalRequests() }
                    nextSocialCheckAt = System.currentTimeMillis() + 5_000L
                }
                if (System.currentTimeMillis() >= nextUpdateCheckAt) {
                    runCatching { checkForUpdate() }
                    nextUpdateCheckAt = System.currentTimeMillis() + 30 * 60 * 1000L
                }
                delay(40)
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
        val session = auth.savedSession ?: return
        val dao = (application as MowellApplication).database.dao()
        val callAlerts = getSharedPreferences("mowell_call_alerts", Context.MODE_PRIVATE)
        val remoteConversations = auth.fetchConversations().getOrNull() ?: return
        dao.retainSyncedConversations(remoteConversations.map { it.id }.toSet())
        val hidden = getSharedPreferences("mowell_hidden_messages", Context.MODE_PRIVATE)

        remoteConversations.forEach remoteLoop@ { remote ->
            val syncState = getSharedPreferences("mowell_notification_watermarks", Context.MODE_PRIVATE)
            val watermarkKey = "${session.user.id}:${remote.id}"
            val notificationFloor = if (syncState.contains(watermarkKey)) syncState.getLong(watermarkKey, 0L) else System.currentTimeMillis().also {
                syncState.edit().putLong(watermarkKey, it).apply()
            }
            var latestIncomingTime = notificationFloor
            val existing = dao.getConversation(remote.id)
            dao.upsertConversation(ConversationEntity(
                remote.id, remote.title, existing?.subtitle ?: "Start chatting", remote.isGroup,
                remote.updatedAt, remote.username, remote.avatarUrl, remote.lastSeenAt, remote.members,
                existing?.unreadCount ?: 0, remote.blocked, remote.blockedByMe
            ))
            val fetched = auth.fetchMessages(remote.id, 0L).getOrNull() ?: return@remoteLoop
            val newIncoming = mutableListOf<MessageEntity>()
            var forcedCallAlert: MessageEntity? = null
            fetched.forEach messageLoop@ { item ->
                if (hidden.getStringSet(item.conversationId, emptySet()).orEmpty().contains(item.id)) return@messageLoop
                if (!item.outgoing) latestIncomingTime = maxOf(latestIncomingTime, item.sentAt)
                val alreadyStored = dao.hasMessage(item.id)
                val message = MessageEntity(
                    item.id, item.conversationId, if (item.outgoing) "You" else item.sender,
                    item.body, item.sentAt, item.outgoing, Route.INTERNET.name, item.delivery, item.kind,
                    item.attachmentId, item.attachmentMime, item.attachmentName
                )
                if (!alreadyStored) {
                    dao.insertMessage(message)
                    if (!item.outgoing) dao.revealConversationOnIncoming(remote.id, item.sentAt)
                    if (!item.outgoing && item.sentAt > notificationFloor) newIncoming += message
                }
                // Calls cannot rely only on the message watermark: the service can
                // start just after an incoming invite was saved. Ring once for any
                // still-fresh call invite that has not already been alerted.
                if (!item.outgoing && item.kind == "call" && item.sentAt >= System.currentTimeMillis() - 45_000L) {
                    val alertKey = "${session.user.id}:${item.id}"
                    if (!callAlerts.getBoolean(alertKey, false)) {
                        callAlerts.edit().putBoolean(alertKey, true).apply()
                        forcedCallAlert = message
                    }
                }
            }
            if (latestIncomingTime > notificationFloor) syncState.edit().putLong(watermarkKey, latestIncomingTime).apply()
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
                val latest = forcedCallAlert ?: newIncoming.last()
                if (latest.kind == "call_end") notifier.clearConversation(remote.id)
                else notifier.show(remote.title, latest, unread.coerceAtLeast(1), remote.avatarUrl)
            } else if (forcedCallAlert != null) {
                notifier.show(remote.title, forcedCallAlert!!, 1, remote.avatarUrl)
            }
        }
    }

    private suspend fun syncMessageChanges() {
        val session = auth.savedSession ?: return
        val state = getSharedPreferences("mowell_fast_message_sync", Context.MODE_PRIVATE)
        val cursorKey = "cursor:${session.user.id}"
        val cursor = if (state.contains(cursorKey)) state.getLong(cursorKey, 0L) else (System.currentTimeMillis() - 2_000L).also {
            state.edit().putLong(cursorKey, it).apply()
        }
        val fetched = auth.fetchMessageChanges(cursor).getOrNull() ?: return
        if (fetched.isEmpty()) return
        val dao = (application as MowellApplication).database.dao()
        val hidden = getSharedPreferences("mowell_hidden_messages", Context.MODE_PRIVATE)
        fetched.forEach { item ->
            if (hidden.getStringSet(item.conversationId, emptySet()).orEmpty().contains(item.id)) return@forEach
            val alreadyStored = dao.hasMessage(item.id)
            val message = MessageEntity(
                item.id, item.conversationId, if (item.outgoing) "You" else item.sender,
                item.body, item.sentAt, item.outgoing, Route.INTERNET.name, item.delivery, item.kind,
                item.attachmentId, item.attachmentMime, item.attachmentName, item.editedAt,
                item.replyToId, item.threadRootId, item.reactions, item.metadata
            )
            dao.insertMessage(message)
            val current = dao.getConversation(item.conversationId)
            if (!alreadyStored && !item.outgoing) {
                dao.revealConversationOnIncoming(item.conversationId, item.sentAt)
                if (item.kind == "call_end") notifier.clearConversation(item.conversationId)
                else {
                    val unread = dao.incrementUnread(item.conversationId)
                    notifier.show(current?.title ?: item.sender, message, unread, current?.avatarUrl)
                }
            }
            if (!item.outgoing && item.kind == "call_end") {
                runCatching { JSONObject(item.body).optString("room") }.getOrNull()?.takeIf { it.isNotBlank() }?.let {
                    CallCoordinator.endIfActive(application, it)
                }
            }
            if (current != null && item.syncAt >= current.updatedAt) {
                dao.upsertConversation(current.copy(subtitle = preview(item.kind, item.body, item.attachmentName), updatedAt = item.sentAt))
            }
        }
        val latest = fetched.maxOfOrNull { it.syncAt } ?: cursor
        if (latest > cursor) state.edit().putLong(cursorKey, latest).apply()
    }

    private suspend fun checkForUpdate() {
        val update = updater.check() ?: return
        val state = getSharedPreferences("mowell_update_notifications", Context.MODE_PRIVATE)
        if (state.getInt("notified_version", 0) >= update.versionCode) return
        notifier.showUpdateAvailable(update.versionName)
        state.edit().putInt("notified_version", update.versionCode).apply()
    }

    private suspend fun syncApprovalRequests() {
        val seen = getSharedPreferences("mowell_approval_notifications", Context.MODE_PRIVATE)
        val notified = seen.getStringSet("ids", emptySet()).orEmpty().toMutableSet()
        auth.fetchConnectionRequests().getOrDefault(emptyList()).filter { it.direction == "incoming" }.forEach { request ->
            val key = "contact:${request.id}"
            if (notified.add(key)) notifier.showConnectionRequest(request.id, request.user.displayName, request.user.username)
        }
        auth.fetchGroupInvitations().getOrDefault(emptyList()).forEach { invitation ->
            val key = "group:${invitation.id}"
            if (notified.add(key)) notifier.showGroupInvitation(invitation.id, invitation.groupTitle, invitation.inviter.displayName)
        }
        seen.edit().putStringSet("ids", notified.toList().takeLast(250).toSet()).apply()
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

        fun start(context: Context): Boolean = runCatching {
            val intent = Intent(context, MessageSyncService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ContextCompat.startForegroundService(context, intent)
            else context.startService(intent)
            true
        }.getOrDefault(false)

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
