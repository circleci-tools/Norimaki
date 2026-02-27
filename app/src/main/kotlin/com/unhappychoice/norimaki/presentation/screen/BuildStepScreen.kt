package com.unhappychoice.norimaki.presentation.screen

import com.github.unhappychoice.circleci.v2.response.Job
import com.github.unhappychoice.circleci.v2.response.Workflow
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.presentation.screen.core.Screen
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.singleton

class BuildStepScreen(val workflow: Workflow, val job: Job) : Screen() {
    override fun getTitle(): String = job.name
    override fun getLayoutResource() = R.layout.build_step_view
    override fun module(activityModule: DI) = DI {
        extend(activityModule)
        bind<Workflow>() with singleton { workflow }
        bind<Job>() with singleton { job }
    }
}
