package com.unhappychoice.norimaki.di.component.screen

import com.unhappychoice.norimaki.presentation.core.scope.ViewScope
import com.unhappychoice.norimaki.presentation.view.APITokenView
import dagger.Subcomponent

@Subcomponent
@ViewScope
interface APITokenScreenComponent {
    fun inject(view: APITokenView)
}
