package com.grapaxels.mowell.call

import android.content.Context
import android.content.Intent
import com.grapaxels.mowell.CallSession

object CallCoordinator {
    fun launch(context: Context, session: CallSession) {
        context.startActivity(Intent(context, MowellCallActivity::class.java).apply {
            putExtra("conversation", session.conversationId)
            putExtra("name", session.name)
            putExtra("room", session.room)
            putExtra("video", session.video)
            putExtra("initiator", session.initiator)
        })
    }

    fun endIfActive(@Suppress("UNUSED_PARAMETER") context: Context, room: String) {
        MowellCallActivity.endRoom(room)
    }
}
