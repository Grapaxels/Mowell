package com.grapaxels.mowell.network

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.grapaxels.mowell.MainActivity
import com.grapaxels.mowell.MowellApplication
import com.grapaxels.mowell.R
import com.grapaxels.mowell.auth.AuthRepository
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

class MessageNotificationService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var auth: AuthRepository
    private lateinit var notifier: MessageNotifier
    private lateinit var loop: Job
    private val cursors by lazy { getSharedPreferences("mowell_notification_sync", Context.MODE_PRIVATE) }
    private val dao by lazy { (application as MowellApplication).database.dao() }
    private val startedAt = System.currentTimeMillis()

    override fun onCreate() {
        super.onCreate()
        auth = AuthRepository(this)
        notifier = MessageNotifier(this)
        createServiceChannel()
        val openApp = PendingIntent.getActivity(
            this,
            SERVICE_NOTIFICATION_ID,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, SERVICE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mowell)
            .setContentTitle("Mowell messages are active")
            .setContentText("Listening securely for new messages")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setContentIntent(openApp)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(SERVICE_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING)
        } else startForeground(SERVICE_NOTIFICATION_ID, notification)
        loop = scope.launch { listenForMessages() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        if (::loop.isInitialized) loop.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun listenForMessages() {
        while (scope.isActive) {
            if (auth.savedSession == null) {
                stopSelf()
                return
            }
            auth.fetchConversations().getOrNull()?.forEach { conversation ->
                val key = "cursor:${conversation.id}"
                val savedCursor = cursors.getLong(key, 0L)
                val localCursor = if (savedCursor == 0L) dao.latestMessageTime(conversation.id) else 0L
                val after = maxOf(savedCursor, localCursor, startedAt - 2_000L)
                auth.fetchMessages(conversation.id, after, false).onSuccess { messages ->
                    messages.asSequence()
                        .filter { !it.outgoing && it.kind != "call_end" }
                        .forEach { item ->
                            notifier.show(
                                conversation.title,
                                MessageEntity(
                                    item.id, item.conversationId, item.sender, item.body, item.sentAt,
                                    false, Route.INTERNET.name, item.delivery, item.kind,
                                    item.attachmentId, item.attachmentMime, item.attachmentName
                                ),
                                avatarUrl = conversation.avatarUrl
                            )
                        }
                    messages.maxOfOrNull { it.sentAt }?.let { latest ->
                        cursors.edit().putLong(key, maxOf(after, latest)).apply()
                    }
                }
            }
            delay(POLL_INTERVAL_MS)
        }
    }

    private fun createServiceChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            SERVICE_CHANNEL_ID,
            "Message connection",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps Mowell connected for new-message notifications"
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val SERVICE_CHANNEL_ID = "mowell_message_connection"
        private const val SERVICE_NOTIFICATION_ID = 13001
        private const val POLL_INTERVAL_MS = 1_500L

        fun start(context: Context) {
            val intent = Intent(context, MessageNotificationService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ContextCompat.startForegroundService(context, intent)
            else context.startService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MessageNotificationService::class.java))
        }
    }
}
