package com.unhappychoice.norimaki.presentation.screen

import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.domain.model.ProjectId
import com.unhappychoice.norimaki.presentation.presenter.FlakyTestsPresenter
import com.unhappychoice.norimaki.presentation.screen.core.Screen
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.provider
import org.kodein.di.singleton

data class FlakyTestsScreen(val projectId: ProjectId) : Screen() {
    override fun getTitle(): String = "Flaky Tests · ${projectId.toPathString()}"
    override fun getLayoutResource() = R.layout.flaky_tests_view

    override fun module(activityModule: DI) = DI {
        extend(activityModule)
        bind<ProjectId>() with singleton { projectId }
        bind<FlakyTestsPresenter>() with provider { FlakyTestsPresenter() }
    }
}
