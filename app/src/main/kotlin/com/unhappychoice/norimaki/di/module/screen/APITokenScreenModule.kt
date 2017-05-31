package com.unhappychoice.norimaki.di.module.screen

import com.unhappychoice.norimaki.MainActivity
import com.unhappychoice.norimaki.presentation.core.scope.ViewScope
import com.unhappychoice.norimaki.presentation.presenter.APITokenPresenter
import com.unhappychoice.norimaki.presentation.screen.APITokenScreen
import dagger.Provides

@dagger.Module
class APITokenScreenModule {
    @Provides @ViewScope fun providePresenter(activity: MainActivity) = APITokenPresenter(activity)
}