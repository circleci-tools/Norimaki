package com.unhappychoice.norimaki.di.module.screen

import com.github.unhappychoice.circleci.CircleCIAPIClientV1
import com.unhappychoice.norimaki.MainActivity
import com.unhappychoice.norimaki.domain.service.EventBusService
import com.unhappychoice.norimaki.infrastructure.pusher.PusherService
import com.unhappychoice.norimaki.presentation.core.scope.ViewScope
import com.unhappychoice.norimaki.presentation.screen.BuildListScreen
import dagger.Provides

@dagger.Module
class BuildListScreenModule {
    @Provides @ViewScope fun providePresenter(
        activity: MainActivity,
        api: CircleCIAPIClientV1,
        eventBus: EventBusService,
        pusher: PusherService
    ) = BuildListScreen.Presenter(activity, api, eventBus, pusher)
}
