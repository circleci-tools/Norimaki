package com.unhappychoice.norimaki.di.component.screen

import com.unhappychoice.norimaki.di.module.screen.BuildScreenModule
import com.unhappychoice.norimaki.presentation.core.scope.ViewScope
import com.unhappychoice.norimaki.presentation.view.BuildView
import dagger.Subcomponent

@Subcomponent(modules = arrayOf(BuildScreenModule::class))
@ViewScope
interface BuildScreenComponent {
    fun inject(view: BuildView)
}
