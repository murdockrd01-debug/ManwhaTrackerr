package com.manhwatracker.app

import android.app.Application
import com.manhwatracker.app.data.CrashLog

class ManhwaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        if (com.manhwatracker.app.data.Prefs.updatesEnabled(this)) com.manhwatracker.app.ext.UpdateWorker.schedule(this)
    }
}
