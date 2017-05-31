package com.unhappychoice.norimaki.di.component

import com.unhappychoice.norimaki.MainActivity
import com.unhappychoice.norimaki.di.component.screen.APITokenScreenComponent
import com.unhappychoice.norimaki.di.component.screen.BuildListScreenComponent
import com.unhappychoice.norimaki.di.component.screen.BuildScreenComponent
import com.unhappychoice.norimaki.di.component.screen.BuildStepScreenComponent
import com.unhappychoice.norimaki.di.module.ActivityModule
import com.unhappychoice.norimaki.di.module.screen.BuildScreenModule
import com.unhappychoice.norimaki.di.module.screen.BuildStepScreenModule
import com.unhappychoice.norimaki.presentation.core.scope.ActivityScope

@dagger.Subcomponent(modules = arrayOf(ActivityModule::class))
@ActivityScope
interface ActivityComponent {
    companion object {
        val name = "activity_component"
    }

    fun inject(activity: MainActivity)
    fun apiTokenScreenComponent(): APITokenScreenComponent
    fun buildListScreenComponent(): BuildListScreenComponent
    fun buildScreenComponent(module: BuildScreenModule): BuildScreenComponent
    fun stepScreenComponent(module: BuildStepScreenModule): BuildStepScreenComponent
}
