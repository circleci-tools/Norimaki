package com.unhappychoice.norimaki.di.component.screen

import com.unhappychoice.norimaki.di.module.screen.BuildStepScreenModule
import com.unhappychoice.norimaki.presentation.core.scope.ViewScope
import com.unhappychoice.norimaki.presentation.view.BuildStepView
import dagger.Subcomponent

@Subcomponent(modules = arrayOf(BuildStepScreenModule::class))
@ViewScope
interface BuildStepScreenComponent {
    fun inject(view: BuildStepView)
}
