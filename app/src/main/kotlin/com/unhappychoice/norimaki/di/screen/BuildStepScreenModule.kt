package com.unhappychoice.norimaki.di.screen

import com.github.salomonbrys.kodein.Kodein
import com.github.salomonbrys.kodein.bind
import com.github.salomonbrys.kodein.provider
import com.github.unhappychoice.circleci.response.Build
import com.github.unhappychoice.circleci.response.BuildStep

fun buildStepScreenModule(build: Build, buildStep: BuildStep) = Kodein.Module {
    bind<Build>() with provider { build }
    bind<BuildStep>() with provider { buildStep }
}
