package com.grapaxels.mowell

import android.app.Application
import com.grapaxels.mowell.data.MowellDatabase

class MowellApplication : Application() {
    val database by lazy { MowellDatabase.create(this) }
}
