package com.unhappychoice.norimaki

import android.app.Application
import mortar.MortarScope

class NorimakiApplication : Application() {
    private val scope by lazy {
        MortarScope.buildRootScope().build("root_scope")
    }

    override fun getSystemService(name: String): Any? {
        return when (scope.hasService(name)) {
            true -> scope.getService(name)
            false -> super.getSystemService(name)
        }
    }
}
