package com.grapaxels.mowell.call

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grapaxels.mowell.network.NotificationActionReceiver

/** Lock-screen-capable incoming call UI. It does not join until Accept is tapped. */
class IncomingCallActivity : ComponentActivity() {
    private val timeoutHandler = Handler(Looper.getMainLooper())
    private val timeout = Runnable { if (!isFinishing) decline("no_answer") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        val name = intent.getStringExtra("name") ?: "Mowell caller"
        val video = intent.getBooleanExtra("video", false)
        setContent { IncomingCallContent(name, video, ::accept, { decline("declined") }) }
        timeoutHandler.postDelayed(timeout, 30_000)
    }

    private fun accept() {
        timeoutHandler.removeCallbacks(timeout)
        cancelNotification()
        startActivity(Intent(this, MowellCallActivity::class.java).apply {
            putExtras(intent)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        })
        finish()
    }

    private fun decline(reason: String) {
        timeoutHandler.removeCallbacks(timeout)
        sendBroadcast(Intent(this, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_DECLINE
            putExtra(NotificationActionReceiver.EXTRA_ROOM, intent.getStringExtra("room"))
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION, intent.getIntExtra("notification_id", 0))
            putExtra("reason", reason)
        })
        cancelNotification()
        finish()
    }

    private fun cancelNotification() {
        intent.getIntExtra("notification_id", 0).takeIf { it != 0 }?.let {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(it)
        }
    }

    override fun onDestroy() {
        timeoutHandler.removeCallbacks(timeout)
        super.onDestroy()
    }
}

@Composable
private fun IncomingCallContent(name: String, video: Boolean, accept: () -> Unit, decline: () -> Unit) {
    MaterialTheme {
        Surface(color = ComposeColor(0xFF08080D), modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 54.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Mowell", color = ComposeColor(0xFF9B83FF), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(Modifier.height(62.dp))
                    Box(
                        Modifier.size(132.dp).clip(CircleShape).background(ComposeColor(0xFF7055E8)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(name.trim().firstOrNull()?.uppercase() ?: "M", color = ComposeColor.White, fontSize = 58.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(28.dp))
                    Text(name, color = ComposeColor.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(10.dp))
                    Text(if (video) "Incoming video call" else "Incoming voice call", color = ComposeColor(0xFFB7B2C6), fontSize = 17.sp)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(
                        onClick = decline,
                        modifier = Modifier.weight(1f).height(62.dp),
                        shape = RoundedCornerShape(22.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ComposeColor(0xFFEF4055))
                    ) { Text("Decline", fontWeight = FontWeight.Bold) }
                    Button(
                        onClick = accept,
                        modifier = Modifier.weight(1f).height(62.dp),
                        shape = RoundedCornerShape(22.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ComposeColor(0xFF35C769))
                    ) { Text("Accept", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}
