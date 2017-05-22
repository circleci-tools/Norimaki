package com.unhappychoice.norimaki

import android.support.multidex.MultiDexApplication
import com.unhappychoice.norimaki.di.component.ApplicationComponent
import com.unhappychoice.norimaki.di.component.DaggerApplicationComponent
import com.unhappychoice.norimaki.di.module.ApplicationModule
import mortar.MortarScope

class NorimakiApplication : MultiDexApplication() {
    private val scope by lazy {
        MortarScope.buildRootScope()
            .withService(ApplicationComponent.name, component)
            .build("root_scope")
    }

    private val component: ApplicationComponent by lazy {
        DaggerApplicationComponent.builder()
            .applicationModule(ApplicationModule(this))
            .build()
            .apply { inject(this@NorimakiApplication) }
    }

    override fun getSystemService(name: String?): Any? {
        return when (scope.hasService(name)) {
            true -> scope.getService(name)
            false -> super.getSystemService(name)
        }
    }
}
