package com.unhappychoice.norimaki.presentation.screen

import com.github.unhappychoice.circleci.response.Build
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.domain.model.revisionString
import com.unhappychoice.norimaki.presentation.screen.core.Screen
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.singleton

class BuildScreen(val build: Build) : Screen() {
    override fun getTitle(): String = build.revisionString()
    override fun getLayoutResource() = R.layout.build_view
    override fun module(activityModule: DI) = DI {
        extend(activityModule)
        bind<Build>() with singleton  { build }
    }
}
