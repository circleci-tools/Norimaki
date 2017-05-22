package com.unhappychoice.norimaki.di.module.screen

import com.github.unhappychoice.circleci.CircleCIAPIClientV1
import com.github.unhappychoice.circleci.response.Build
import com.github.unhappychoice.circleci.response.BuildStep
import com.unhappychoice.norimaki.MainActivity
import com.unhappychoice.norimaki.domain.service.EventBusService
import com.unhappychoice.norimaki.infrastructure.pusher.PusherService
import com.unhappychoice.norimaki.presentation.core.scope.ViewScope
import com.unhappychoice.norimaki.presentation.screen.BuildStepScreen
import dagger.Provides

@dagger.Module
class BuildStepScreenModule(val build: Build, val buildStep: BuildStep, val stepIndex: Int) {
    @Provides @ViewScope fun providePresenter(
        activity: MainActivity,
        api: CircleCIAPIClientV1,
        eventBus: EventBusService,
        pusher: PusherService
    ) = BuildStepScreen.Presenter(build, buildStep, stepIndex, activity, api, eventBus, pusher)
}
