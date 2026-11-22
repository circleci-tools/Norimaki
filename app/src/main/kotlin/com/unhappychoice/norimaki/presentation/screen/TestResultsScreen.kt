package com.unhappychoice.norimaki.presentation.screen

import com.github.unhappychoice.circleci.v1.response.Build
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.presentation.presenter.TestResultsPresenter
import com.unhappychoice.norimaki.presentation.screen.core.Screen
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.provider
import org.kodein.di.singleton

class TestResultsScreen(val build: Build) : Screen() {
    override fun getTitle(): String = "Test Results"
    override fun getLayoutResource() = R.layout.test_results_view

    override fun module(activityModule: DI) = DI {
        extend(activityModule)
        bind<Build>() with singleton { build }
        bind<TestResultsPresenter>() with provider { TestResultsPresenter() }
    }
}
