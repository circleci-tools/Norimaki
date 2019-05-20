package com.unhappychoice.norimaki

import androidx.multidex.MultiDexApplication
import mortar.MortarScope

class NorimakiApplication : MultiDexApplication() {
    private val scope by lazy {
        MortarScope.buildRootScope().build("root_scope")
    }

    override fun getSystemService(name: String?): Any? {
        return when (scope.hasService(name)) {
            true -> scope.getService(name)
            false -> super.getSystemService(name)
        }
    }
}
