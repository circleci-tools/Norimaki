package com.unhappychoice.norimaki.presentation.presenter

import com.github.unhappychoice.circleci.v1.response.Build
import com.github.unhappychoice.circleci.v1.response.BuildStep
import com.unhappychoice.norimaki.domain.model.isFinished
import com.unhappychoice.norimaki.domain.model.vcsType
import com.unhappychoice.norimaki.extension.*
import com.unhappychoice.norimaki.presentation.presenter.core.PresenterNeedsToken
import com.unhappychoice.norimaki.presentation.screen.BuildStepScreen
import com.unhappychoice.norimaki.presentation.view.BuildView
import io.reactivex.Observable
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.switchLatest
import mortar.MortarScope
import org.kodein.di.instance

class BuildPresenter : PresenterNeedsToken<BuildView>() {
    val build: Build by instance()
    val steps = Variable<List<BuildStep>>(listOf())

    override fun onEnterScope(scope: MortarScope?) {
        super.onEnterScope(scope)
        pollBuild()
    }

    fun pollBuild() {
        polling(BUILD_POLLING_SECONDS) { getBuildAPI() }
            .takeUntil { it.isFinished() }
            .subscribeOnIoObserveOnUI()
            .subscribeNext { steps.value = it.steps ?: listOf() }
            .addTo(bag)
    }

    fun goToBuildStepScreen(buildStep: BuildStep) {
        if (buildStep.actions.isEmpty()) return
        goTo(activity, BuildStepScreen(build, buildStep))
    }

    fun rebuild() {
        api.retryBuild(build.vcsType(), build.username!!, build.reponame!!, build.buildNum!!)
            .subscribeOnIoObserveOnUI()
            .subscribeNext { goBack(activity) }
            .addTo(bag)
    }

    fun rebuildWithoutCache() {
        api.deleteCache(build.vcsType(), build.username!!, build.reponame!!)
            .map {
                api.retryBuild(build.vcsType(), build.username!!, build.reponame!!, build.buildNum!!).subscribeOnIoObserveOnUI()
            }.switchLatest()
            .subscribeOnIoObserveOnUI()
            .subscribeNext { goBack(activity) }
            .addTo(bag)
    }

    private fun getBuildAPI(): Observable<Build> =
        api.getBuild(build.vcsType(), build.username!!, build.reponame!!, build.buildNum!!)

    companion object {
        const val BUILD_POLLING_SECONDS = 5L
    }
}
