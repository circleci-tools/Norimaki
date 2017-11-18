package com.unhappychoice.norimaki.presentation.screen

import com.github.salomonbrys.kodein.Kodein
import com.github.unhappychoice.circleci.response.Build
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.di.screen.buildScreenModule
import com.unhappychoice.norimaki.domain.model.revisionString
import com.unhappychoice.norimaki.presentation.screen.core.Screen

class BuildScreen(val build: Build) : Screen() {
    override fun getTitle(): String = build.revisionString()
    override fun getLayoutResource() = R.layout.build_view
    override fun module(activityModule: Kodein) = Kodein {
        extend(activityModule)
        import(buildScreenModule(build))
    }
}
