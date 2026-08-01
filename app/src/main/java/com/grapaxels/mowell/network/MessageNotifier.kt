package com.grapaxels.mowell.network

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.grapaxels.mowell.MainActivity
import com.grapaxels.mowell.R
import com.grapaxels.mowell.call.MowellCallActivity
import com.grapaxels.mowell.data.MessageEntity
import org.json.JSONObject

class MessageNotifier(private val context: Context) {
    private val channelId = "mowell_messages"
    private val notificationPrefs = context.getSharedPreferences("mowell_notification_ids", Context.MODE_PRIVATE)

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Messages and calls", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "New Mowell messages, files, and call invitations"
                enableVibration(true)
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    fun show(conversationTitle: String, message: MessageEntity, totalUnread: Int = 1, avatarUrl: String? = null) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val call = message.kind == "call"
        val notificationId = (message.conversationId + message.id).hashCode()
        val preview = when (message.kind) {
            "call" -> "Incoming ${if (message.body.contains("\"video\":true")) "video" else "voice"} call"
            "call_end" -> "Call ended"
            "image" -> "Sent a photo"
            "video" -> "Sent a video"
            "audio" -> "Sent audio"
            "file" -> "Sent ${message.attachmentName ?: "a file"}"
            "location" -> "Shared a location"
            "contact" -> "Shared a contact"
            else -> message.body
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("conversation_id", message.conversationId)
        }
        val pendingIntent = PendingIntent.getActivity(context, message.conversationId.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_mowell)
            .setLargeIcon(BitmapFactory.decodeResource(context.resources, R.drawable.mowell_logo))
            .setContentTitle(if (call) preview else conversationTitle)
            .setContentText(if (call) "$conversationTitle is calling" else preview)
            .setStyle(NotificationCompat.BigTextStyle().bigText(if (call) "$conversationTitle is calling" else preview))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setNumber(totalUnread.coerceAtLeast(1))
            .setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)
            .setCategory(if (call) NotificationCompat.CATEGORY_CALL else NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        if (call) {
            val data = runCatching { JSONObject(message.body) }.getOrNull()
            val room = data?.optString("room").orEmpty()
            val video = data?.optBoolean("video") ?: false
            val group = data?.optBoolean("group") ?: false
            if (room.isNotBlank()) {
                val accept = Intent(context, MowellCallActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("conversation", message.conversationId); putExtra("name", conversationTitle)
                    putExtra("room", room); putExtra("video", video); putExtra("group", group); putExtra("initiator", false)
                    putExtra("avatar", avatarUrl)
                    putExtra("notification_id", notificationId)
                }
                val acceptPending = PendingIntent.getActivity(context, notificationId, accept, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                val decline = Intent(context, NotificationActionReceiver::class.java).apply {
                    action = NotificationActionReceiver.ACTION_DECLINE
                    putExtra(NotificationActionReceiver.EXTRA_ROOM, room)
                    putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION, notificationId)
                }
                val declinePending = PendingIntent.getBroadcast(context, notificationId + 1, decline, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                builder.addAction(0, "Decline", declinePending).addAction(0, "Accept", acceptPending).setOngoing(true)
            }
        } else if (message.kind != "call_end") {
            val replyIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_REPLY
                putExtra(NotificationActionReceiver.EXTRA_CONVERSATION, message.conversationId)
                putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION, notificationId)
            }
            val mutable = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            val replyPending = PendingIntent.getBroadcast(context, notificationId, replyIntent, PendingIntent.FLAG_UPDATE_CURRENT or mutable)
            val remoteInput = RemoteInput.Builder(NotificationActionReceiver.KEY_REPLY).setLabel("Reply to $conversationTitle").build()
            builder.addAction(NotificationCompat.Action.Builder(0, "Reply", replyPending).addRemoteInput(remoteInput).setAllowGeneratedReplies(true).build())
        }
        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        val key = "conversation:${message.conversationId}"
        val ids = notificationPrefs.getStringSet(key, emptySet()).orEmpty().toMutableSet().apply { add(notificationId.toString()) }
        notificationPrefs.edit().putStringSet(key, ids).apply()
    }

    fun clearConversation(conversationId: String) {
        val key = "conversation:$conversationId"
        notificationPrefs.getStringSet(key, emptySet()).orEmpty().forEach {
            it.toIntOrNull()?.let { id -> NotificationManagerCompat.from(context).cancel(id) }
        }
        notificationPrefs.edit().remove(key).apply()
    }
}
