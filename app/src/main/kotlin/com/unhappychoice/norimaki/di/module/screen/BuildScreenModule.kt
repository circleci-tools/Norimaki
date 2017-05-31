package com.unhappychoice.norimaki.di.module.screen

import com.github.unhappychoice.circleci.CircleCIAPIClientV1
import com.github.unhappychoice.circleci.response.Build
import com.unhappychoice.norimaki.MainActivity
import com.unhappychoice.norimaki.domain.service.EventBusService
import com.unhappychoice.norimaki.infrastructure.pusher.PusherService
import com.unhappychoice.norimaki.presentation.core.scope.ViewScope
import com.unhappychoice.norimaki.presentation.presenter.BuildPresenter
import com.unhappychoice.norimaki.presentation.screen.BuildScreen
import dagger.Provides

@dagger.Module
class BuildScreenModule(val build: Build) {
    @Provides @ViewScope fun providePresenter(
        activity: MainActivity,
        api: CircleCIAPIClientV1,
        eventBus: EventBusService,
        pusher: PusherService
    ) = BuildPresenter(build, activity, api, eventBus, pusher)
}
