package com.manhwatracker.app

import android.app.Application
import com.manhwatracker.app.data.CrashLog

class ManhwaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
    }
}
