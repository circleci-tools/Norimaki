package com.unhappychoice.norimaki.presentation.presenter

import com.github.unhappychoice.circleci.response.Build
import com.github.unhappychoice.circleci.response.BuildStep
import com.unhappychoice.norimaki.domain.model.addAction
import com.unhappychoice.norimaki.extension.*
import com.unhappychoice.norimaki.presentation.presenter.core.PresenterNeedsToken
import com.unhappychoice.norimaki.presentation.screen.BuildStepScreen
import com.unhappychoice.norimaki.presentation.view.BuildView
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.switchLatest
import mortar.MortarScope
import org.kodein.di.instance

class BuildPresenter : PresenterNeedsToken<BuildView>() {
    val build: Build by instance()
    val steps = Variable<List<BuildStep>>(listOf())

    override fun onEnterScope(scope: MortarScope?) {
        super.onEnterScope(scope)

        pusher.newActionEvents(build)
            .map { BuildStep(name = it.log.name, actions = listOf(it.log.toBuildAction())) }
            .filterNotNull()
            .subscribeNext { steps.value = steps.value + it }
            .addTo(bag)

        pusher.updateActionEvents(build)
            .map { it.log.toBuildAction() }
            .subscribeNext { steps.value = steps.value.addAction(it) }
            .addTo(bag)

        getBuild()
    }

    fun getBuild() {
        api.getBuild(build.username!!, build.reponame!!, build.buildNum!!)
            .subscribeOnIoObserveOnUI()
            .subscribeNext { steps.value = steps.value + (it.steps ?: listOf()) }
            .addTo(bag)
    }

    fun goToBuildStepScreen(buildStep: BuildStep) {
        if (buildStep.actions.isEmpty()) return
        goTo(activity, BuildStepScreen(build, buildStep))
    }

    fun rebuild() {
        api.retryBuild(build.username!!, build.reponame!!, build.buildNum!!)
            .subscribeOnIoObserveOnUI()
            .subscribeNext { goBack(activity) }
            .addTo(bag)
    }

    fun rebuildWithoutCache() {
        api.deleteCache(build.username!!, build.reponame!!)
            .map {
                api.retryBuild(build.username!!, build.reponame!!, build.buildNum!!).subscribeOnIoObserveOnUI()
            }.switchLatest()
            .subscribeOnIoObserveOnUI()
            .subscribeNext { goBack(activity) }
            .addTo(bag)
    }
}