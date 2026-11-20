package com.unhappychoice.norimaki

import android.app.Application
import com.unhappychoice.norimaki.di.applicationModule
import mortar.MortarScope
import org.kodein.di.DI

open class NorimakiApplication : Application() {
    open val module by lazy { DI { import(applicationModule(this@NorimakiApplication)) } }

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
