package com.grapaxels.mowell.network

import android.content.Context
import android.provider.Settings

object NotificationPreferences {
    private const val FILE = "mowell_notification_settings"
    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun floating(context: Context) = prefs(context).getBoolean("floating", true)
    fun setFloating(context: Context, enabled: Boolean) = prefs(context).edit().putBoolean("floating", enabled).apply()

    fun sendSound(context: Context) = prefs(context).getBoolean("send_sound", true)
    fun setSendSound(context: Context, enabled: Boolean) = prefs(context).edit().putBoolean("send_sound", enabled).apply()

    fun messageSound(context: Context, conversationId: String? = null): String {
        val configured = conversationId?.let { prefs(context).getString("conversation_sound:$it", null) }
            ?: prefs(context).getString("message_sound", null)
        return configured ?: Settings.System.DEFAULT_NOTIFICATION_URI.toString()
    }

    fun callSound(context: Context): String = prefs(context).getString("call_sound", null)
        ?: Settings.System.DEFAULT_RINGTONE_URI.toString()

    fun setMessageSound(context: Context, uri: String) = prefs(context).edit().putString("message_sound", uri).apply()
    fun setCallSound(context: Context, uri: String) = prefs(context).edit().putString("call_sound", uri).apply()
    fun setConversationSound(context: Context, conversationId: String, uri: String) =
        prefs(context).edit().putString("conversation_sound:$conversationId", uri).apply()
}
