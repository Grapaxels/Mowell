package com.grapaxels.mowell.network

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import com.grapaxels.mowell.MainActivity
import com.grapaxels.mowell.R
import com.grapaxels.mowell.call.MowellCallActivity
import com.grapaxels.mowell.call.IncomingCallActivity
import com.grapaxels.mowell.data.MessageEntity
import org.json.JSONObject

class MessageNotifier(private val context: Context) {
    private val notificationPrefs = context.getSharedPreferences("mowell_notification_ids", Context.MODE_PRIVATE)

    fun show(conversationTitle: String, message: MessageEntity, totalUnread: Int = 1, avatarUrl: String? = null) {
        val call = message.kind == "call"
        val canNotify = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!canNotify && !call) return
        if (!claim(message.id)) return
        val floating = NotificationPreferences.floating(context)
        val sound = if (call) NotificationPreferences.callSound(context) else NotificationPreferences.messageSound(context, message.conversationId)
        val channelId = if (call) "mowell_calls_${sound.hashCode()}" else "mowell_messages_${floating}_${message.conversationId.hashCode()}_${sound.hashCode()}"
        ensureChannel(channelId, call, floating, sound, conversationTitle)
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
            .setPriority(if (call || floating) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setSound(Uri.parse(sound))
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
                val incoming = Intent(context, IncomingCallActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("conversation", message.conversationId); putExtra("name", conversationTitle)
                    putExtra("room", room); putExtra("video", video); putExtra("group", group); putExtra("initiator", false)
                    putExtra("avatar", avatarUrl); putExtra("notification_id", notificationId)
                }
                val incomingPending = PendingIntent.getActivity(context, notificationId + 2, incoming, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                val decline = Intent(context, NotificationActionReceiver::class.java).apply {
                    action = NotificationActionReceiver.ACTION_DECLINE
                    putExtra(NotificationActionReceiver.EXTRA_ROOM, room)
                    putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION, notificationId)
                }
                val declinePending = PendingIntent.getBroadcast(context, notificationId + 1, decline, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                val caller = Person.Builder().setName(conversationTitle).setImportant(true).build()
                builder.setStyle(NotificationCompat.CallStyle.forIncomingCall(caller, declinePending, acceptPending))
                    .setContentIntent(incomingPending)
                    .setFullScreenIntent(incomingPending, true)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setOngoing(true)
                    .setTimeoutAfter(30_000)
                // Open immediately when Android permits a foreground launch.
                // The full-screen intent above remains the background/lock-screen path.
                runCatching { context.startActivity(incoming) }
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
        if (canNotify) NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        val key = "conversation:${message.conversationId}"
        val ids = notificationPrefs.getStringSet(key, emptySet()).orEmpty().toMutableSet().apply { add(notificationId.toString()) }
        notificationPrefs.edit().putStringSet(key, ids).apply()
    }

    private fun claim(messageId: String): Boolean = synchronized(deliveryLock) {
        val key = "delivered_message_ids"
        val delivered = notificationPrefs.getStringSet(key, emptySet()).orEmpty().toMutableSet()
        if (messageId in delivered) return@synchronized false
        if (delivered.size >= 512) delivered.clear()
        delivered += messageId
        notificationPrefs.edit().putStringSet(key, delivered).commit()
    }

    fun showConnectionRequest(displayName: String) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val sound = NotificationPreferences.messageSound(context, "connections")
        val channelId = "mowell_connections_${sound.hashCode()}"
        ensureChannel(channelId, false, true, sound, "Connection requests")
        val intent = Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP }
        val pendingIntent = PendingIntent.getActivity(context, "connection-requests".hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_mowell)
            .setLargeIcon(BitmapFactory.decodeResource(context.resources, R.drawable.mowell_logo))
            .setContentTitle("New connection request")
            .setContentText("$displayName wants to connect with you")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$displayName wants to connect. Open Mowell to accept or decline."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setSound(Uri.parse(sound))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        NotificationManagerCompat.from(context).notify("connection:$displayName".hashCode(), notification)
    }

    private fun ensureChannel(id: String, call: Boolean, floating: Boolean, sound: String, title: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val importance = if (call || floating) NotificationManager.IMPORTANCE_HIGH else NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(id, if (call) "Incoming calls" else "$title messages", importance).apply {
            description = if (call) "Mowell incoming call ringtone" else "Messages from $title"
            enableVibration(true)
            setSound(Uri.parse(sound), AudioAttributes.Builder()
                .setUsage(if (call) AudioAttributes.USAGE_NOTIFICATION_RINGTONE else AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun clearConversation(conversationId: String) {
        val key = "conversation:$conversationId"
        notificationPrefs.getStringSet(key, emptySet()).orEmpty().forEach {
            it.toIntOrNull()?.let { id -> NotificationManagerCompat.from(context).cancel(id) }
        }
        notificationPrefs.edit().remove(key).apply()
    }

    private companion object {
        val deliveryLock = Any()
    }
}
