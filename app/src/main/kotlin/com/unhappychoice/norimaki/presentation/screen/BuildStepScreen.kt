package com.unhappychoice.norimaki.presentation.screen

import com.github.salomonbrys.kodein.*
import com.github.unhappychoice.circleci.response.Build
import com.github.unhappychoice.circleci.response.BuildStep
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.presentation.screen.core.Screen

class BuildStepScreen(val build: Build, val buildStep: BuildStep) : Screen() {
    override fun getTitle(): String = buildStep.name
    override fun getLayoutResource() = R.layout.build_step_view
    override fun module(activityModule: Kodein) = Kodein {
        extend(activityModule)
        bind<Build>() with singleton { build }
        bind<BuildStep>() with singleton { buildStep }
    }
}