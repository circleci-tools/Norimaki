package com.unhappychoice.norimaki.di.screen

import com.github.salomonbrys.kodein.Kodein
import com.github.salomonbrys.kodein.bind
import com.github.salomonbrys.kodein.provider
import com.github.unhappychoice.circleci.response.Build

fun buildScreenModule(build: Build) = Kodein.Module {
    bind<Build>() with provider { build }
}
