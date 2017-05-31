package com.unhappychoice.norimaki.presentation.screen

import com.github.unhappychoice.circleci.response.Build
import com.github.unhappychoice.circleci.response.BuildStep
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.di.component.ActivityComponent
import com.unhappychoice.norimaki.di.module.screen.BuildStepScreenModule
import com.unhappychoice.norimaki.presentation.screen.core.Screen

class BuildStepScreen(val build: Build, val buildStep: BuildStep, val stepIndex: Int) : Screen() {
    override fun getTitle(): String = buildStep.name
    override fun getLayoutResource() = R.layout.build_step_view
    override fun getSubComponent(activityComponent: ActivityComponent) =
        activityComponent.stepScreenComponent(BuildStepScreenModule(build, buildStep, stepIndex))
}