package com.unhappychoice.norimaki.di.component

import com.unhappychoice.norimaki.NorimakiApplication
import com.unhappychoice.norimaki.di.module.ActivityModule
import com.unhappychoice.norimaki.di.module.ApplicationModule
import javax.inject.Singleton

@dagger.Component(modules = arrayOf(ApplicationModule::class))
@Singleton
interface ApplicationComponent {
    companion object {
        val name = "application_component"
    }

    fun inject(application: NorimakiApplication)
    fun activityComponent(module: ActivityModule): ActivityComponent
}

