package com.unhappychoice.norimaki.di.component.screen

import com.unhappychoice.norimaki.presentation.core.scope.ViewScope
import com.unhappychoice.norimaki.presentation.view.BuildListView
import dagger.Subcomponent

@Subcomponent
@ViewScope interface BuildListScreenComponent {
    fun inject(view: BuildListView)
}
