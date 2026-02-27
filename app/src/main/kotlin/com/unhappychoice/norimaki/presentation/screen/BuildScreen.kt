package com.unhappychoice.norimaki.presentation.screen

import com.github.unhappychoice.circleci.v2.response.Workflow
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.presentation.screen.core.Screen
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.singleton

class BuildScreen(val workflow: Workflow) : Screen() {
    override fun getTitle(): String = "${workflow.name} #${workflow.pipelineNumber ?: ""}"
    override fun getLayoutResource() = R.layout.build_view
    override fun module(activityModule: DI) = DI {
        extend(activityModule)
        bind<Workflow>() with singleton { workflow }
    }
}
