package com.unhappychoice.norimaki

import android.app.Application
import com.unhappychoice.norimaki.infrastructure.notification.BuildNotificationScheduler
import mortar.MortarScope

class NorimakiApplication : Application() {
    private val scope by lazy {
        MortarScope.buildRootScope().build("root_scope")
    }

    override fun onCreate() {
        super.onCreate()
        BuildNotificationScheduler.schedule(this)
    }

    override fun getSystemService(name: String): Any? {
        return when (scope.hasService(name)) {
            true -> scope.getService(name)
            false -> super.getSystemService(name)
        }
    }
}
