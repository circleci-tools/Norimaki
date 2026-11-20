package com.unhappychoice.norimaki.presentation.screen

import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.domain.model.BuildId
import com.unhappychoice.norimaki.domain.model.BuildStepId
import com.unhappychoice.norimaki.presentation.screen.core.Screen
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.singleton

data class BuildStepScreen(val buildId: BuildId, val stepId: BuildStepId) : Screen() {
    override fun getTitle(): String = stepId.legacyName
        ?: (stepId.number ?: stepId.position)?.let { "Step $it" }
        ?: "Build step"
    override fun getLayoutResource() = R.layout.build_step_view

    override fun module(activityModule: DI) = DI {
        extend(activityModule)
        bind<BuildId>() with singleton { buildId }
        bind<BuildStepId>() with singleton { stepId }
    }
}
