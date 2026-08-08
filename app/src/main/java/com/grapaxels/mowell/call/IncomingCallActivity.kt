package com.grapaxels.mowell.call

import android.app.AlertDialog
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Build
import android.view.Gravity
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.grapaxels.mowell.network.NotificationActionReceiver

/** Full-screen call alert used by the notification when Android permits it. */
class IncomingCallActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        val name = intent.getStringExtra("name").orEmpty().ifBlank { "Mowell user" }
        val video = intent.getBooleanExtra("video", false)
        val darkMode = getSharedPreferences("mowell_ui", Context.MODE_PRIVATE).getBoolean("dark_mode", false)
        val backgroundColor = if (darkMode) Color.rgb(15, 15, 16) else Color.WHITE
        val foreground = if (darkMode) Color.rgb(244, 242, 248) else Color.rgb(20, 19, 24)
        val muted = if (darkMode) Color.rgb(170, 166, 178) else Color.rgb(114, 112, 120)
        window.statusBarColor = backgroundColor
        window.navigationBarColor = backgroundColor
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(36, 36, 36, 36)
            setBackgroundColor(backgroundColor)
        }
        root.addView(TextView(this).apply {
            text = name.take(1).uppercase(); setTextColor(Color.WHITE); textSize = 42f; gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.rgb(104, 82, 214)) }
        }, LinearLayout.LayoutParams(dp(112), dp(112)).apply { bottomMargin = dp(24) })
        root.addView(TextView(this).apply { text = "Incoming ${if (video) "video" else "voice"} call"; setTextColor(muted); textSize = 16f; gravity = Gravity.CENTER })
        root.addView(TextView(this).apply { text = name; setTextColor(foreground); textSize = 30f; gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD; setPadding(0, dp(8), 0, dp(32)) })
        fun button(label: String, color: Int, action: () -> Unit) = Button(this).apply {
            text = label; setTextColor(Color.WHITE); isAllCaps = false; textSize = 16f; typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(color) }
            setOnClickListener { action() }
        }
        root.addView(button("Accept", Color.rgb(67, 160, 71)) { accept() }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 12 })
        root.addView(button("Reply and decline", Color.rgb(104, 82, 214)) { replyAndDecline() }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 12 })
        root.addView(button("Decline", Color.rgb(211, 47, 47)) { decline(); finish() })
        setContentView(root)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun accept() {
        val target = Intent(this, MowellCallActivity::class.java).apply {
            putExtras(intent.extras ?: Bundle())
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(target); finish()
    }

    private fun decline(reply: String? = null) {
        sendBroadcast(Intent(this, NotificationActionReceiver::class.java).apply {
            action = if (reply.isNullOrBlank()) NotificationActionReceiver.ACTION_DECLINE else NotificationActionReceiver.ACTION_DECLINE_WITH_REPLY
            putExtra(NotificationActionReceiver.EXTRA_ROOM, intent.getStringExtra("room"))
            putExtra(NotificationActionReceiver.EXTRA_CONVERSATION, intent.getStringExtra("conversation"))
            putExtra(NotificationActionReceiver.EXTRA_REPLY_TEXT, reply)
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION, intent.getIntExtra("notification_id", 0))
        })
    }

    private fun replyAndDecline() {
        val field = EditText(this).apply { hint = "Write a reply"; maxLines = 3 }
        AlertDialog.Builder(this).setTitle("Reply to caller").setView(field)
            .setNegativeButton("Cancel", null).setPositiveButton("Send") { _, _ -> decline(field.text.toString().trim()); finish() }.show()
    }

    override fun onDestroy() {
        intent.getIntExtra("notification_id", 0).takeIf { it != 0 }?.let { (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(it) }
        super.onDestroy()
    }
}
