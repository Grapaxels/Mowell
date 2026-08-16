package com.grapaxels.mowell.call

import android.content.Context
import android.content.Intent
import com.grapaxels.mowell.CallSession

object CallCoordinator {
    fun launch(context: Context, session: CallSession) {
        val active = MowellCallActivity.isRoomActive(session.room)
        val destination = if (session.initiator || active) MowellCallActivity::class.java else IncomingCallActivity::class.java
        context.startActivity(Intent(context, destination).apply {
            if (active) addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            putExtra("conversation", session.conversationId)
            putExtra("name", session.name)
            putExtra("room", session.room)
            putExtra("video", session.video)
            putExtra("initiator", session.initiator)
            putExtra("group", session.group)
            putExtra("avatar", session.avatarUrl)
        })
    }

    fun endIfActive(@Suppress("UNUSED_PARAMETER") context: Context, room: String) {
        MowellCallActivity.endRoom(room)
    }
    fun hangupConversation(conversationId: String) = MowellCallActivity.hangupConversation(conversationId)
}
