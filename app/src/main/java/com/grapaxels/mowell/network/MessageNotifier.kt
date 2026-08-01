package com.grapaxels.mowell.network

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.grapaxels.mowell.MainActivity
import com.grapaxels.mowell.R
import com.grapaxels.mowell.data.MessageEntity

class MessageNotifier(private val context: Context) {
    private val channelId = "mowell_messages"

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Messages and calls", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "New Mowell messages, files, and call invitations"
                enableVibration(true)
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    fun show(conversationTitle: String, message: MessageEntity) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val call = message.kind == "call"
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
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_mowell)
            .setContentTitle(if (call) preview else conversationTitle)
            .setContentText(if (call) "$conversationTitle is calling" else preview)
            .setStyle(NotificationCompat.BigTextStyle().bigText(if (call) "$conversationTitle is calling" else preview))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(if (call) NotificationCompat.CATEGORY_CALL else NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        NotificationManagerCompat.from(context).notify((message.conversationId + message.id).hashCode(), notification)
    }
}
