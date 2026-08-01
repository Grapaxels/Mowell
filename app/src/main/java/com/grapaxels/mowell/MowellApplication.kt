package com.grapaxels.mowell

import android.app.Application
import com.grapaxels.mowell.auth.AuthRepository
import com.grapaxels.mowell.data.MowellDatabase
import com.grapaxels.mowell.network.MessageSyncService

class MowellApplication : Application() {
    val database by lazy { MowellDatabase.create(this) }

    override fun onCreate() {
        super.onCreate()
        if (AuthRepository(this).savedSession != null) MessageSyncService.start(this)
    }
}
