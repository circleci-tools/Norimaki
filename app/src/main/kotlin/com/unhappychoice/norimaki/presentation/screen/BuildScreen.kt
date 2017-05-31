package com.unhappychoice.norimaki.presentation.screen

import com.github.unhappychoice.circleci.response.Build
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.di.component.ActivityComponent
import com.unhappychoice.norimaki.di.module.screen.BuildScreenModule
import com.unhappychoice.norimaki.domain.model.revisionString
import com.unhappychoice.norimaki.presentation.screen.core.Screen

class BuildScreen(val build: Build) : Screen() {
    override fun getTitle(): String = build.revisionString()
    override fun getLayoutResource() = R.layout.build_view
    override fun getSubComponent(activityComponent: ActivityComponent) =
        activityComponent.buildScreenComponent(BuildScreenModule(build))
}