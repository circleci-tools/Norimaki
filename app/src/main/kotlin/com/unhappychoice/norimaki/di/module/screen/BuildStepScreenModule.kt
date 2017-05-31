package com.unhappychoice.norimaki.di.module.screen

import com.github.unhappychoice.circleci.response.Build
import com.github.unhappychoice.circleci.response.BuildStep
import com.unhappychoice.norimaki.presentation.core.scope.ViewScope
import dagger.Provides

@dagger.Module
class BuildStepScreenModule(val build: Build, val buildStep: BuildStep, val stepIndex: Int) {
    @Provides @ViewScope fun provideBuild(): Build = build
    @Provides @ViewScope fun provideBuildStep(): BuildStep = buildStep
    @Provides @ViewScope fun provideStepIndex(): Int = stepIndex
}
