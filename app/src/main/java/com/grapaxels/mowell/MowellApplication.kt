package com.grapaxels.mowell

import android.app.Application
import com.grapaxels.mowell.data.MowellDatabase
import com.grapaxels.mowell.call.CallCoordinator

class MowellApplication : Application() {
    val database by lazy { MowellDatabase.create(this) }
    override fun onCreate() {
        super.onCreate()
        CallCoordinator.initialize(this)
    }
}
