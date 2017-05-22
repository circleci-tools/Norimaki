package com.unhappychoice.norimaki.di.component.screen

import com.unhappychoice.norimaki.di.module.screen.BuildListScreenModule
import com.unhappychoice.norimaki.presentation.core.scope.ViewScope
import com.unhappychoice.norimaki.presentation.view.BuildListView
import dagger.Subcomponent

@Subcomponent(modules = arrayOf(BuildListScreenModule::class))
@ViewScope interface BuildListScreenComponent {
    fun inject(view: BuildListView)
}
