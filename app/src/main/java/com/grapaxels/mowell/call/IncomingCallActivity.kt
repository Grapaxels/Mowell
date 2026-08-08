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
        val backgroundColor = Color.rgb(12, 12, 14)
        val foreground = Color.WHITE
        val muted = Color.rgb(184, 182, 192)
        window.statusBarColor = backgroundColor
        window.navigationBarColor = backgroundColor
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(dp(28), dp(48), dp(28), dp(34))
            setBackgroundColor(backgroundColor)
        }
        root.addView(TextView(this).apply { text = "Mowell"; setTextColor(muted); textSize = 15f; gravity = Gravity.CENTER })
        root.addView(TextView(this).apply { text = name; setTextColor(foreground); textSize = 32f; gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD; maxLines = 2 }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        root.addView(TextView(this).apply { text = "incoming ${if (video) "video" else "audio"} call"; setTextColor(muted); textSize = 16f; gravity = Gravity.CENTER }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        root.addView(android.view.View(this), LinearLayout.LayoutParams(1, 0, 1f))
        root.addView(TextView(this).apply {
            text = name.take(1).uppercase(); setTextColor(Color.WHITE); textSize = 48f; gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.rgb(104, 82, 214)) }
        }, LinearLayout.LayoutParams(dp(124), dp(124)))
        root.addView(android.view.View(this), LinearLayout.LayoutParams(1, 0, 1f))
        root.addView(Button(this).apply {
            text = "Message"; setTextColor(Color.WHITE); isAllCaps = false; textSize = 14f
            background = GradientDrawable().apply { cornerRadius = dp(22).toFloat(); setColor(Color.rgb(47, 47, 52)) }
            setOnClickListener { replyAndDecline() }
        }, LinearLayout.LayoutParams(dp(132), dp(46)).apply { bottomMargin = dp(28) })
        fun callButton(label: String, color: Int, action: () -> Unit) = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            addView(Button(this@IncomingCallActivity).apply {
                text = if (label == "Accept") "●" else "—"; setTextColor(Color.WHITE); textSize = 21f
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
                setOnClickListener { isEnabled = false; action() }
            }, LinearLayout.LayoutParams(dp(72), dp(72)))
            addView(TextView(this@IncomingCallActivity).apply { text = label; setTextColor(Color.WHITE); textSize = 14f; gravity = Gravity.CENTER }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(8) })
        }
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            addView(callButton("Decline", Color.rgb(237, 63, 66)) { decline(); finish() }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(callButton("Accept", Color.rgb(52, 199, 89)) { accept() }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        root.addView(actions, LinearLayout.LayoutParams(-1, -2))
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
