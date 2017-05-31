package com.unhappychoice.norimaki.presentation.presenter

import com.github.unhappychoice.circleci.CircleCIAPIClientV1
import com.github.unhappychoice.circleci.response.Build
import com.github.unhappychoice.circleci.response.BuildStep
import com.unhappychoice.norimaki.MainActivity
import com.unhappychoice.norimaki.domain.model.addAction
import com.unhappychoice.norimaki.domain.service.EventBusService
import com.unhappychoice.norimaki.extension.*
import com.unhappychoice.norimaki.infrastructure.pusher.PusherService
import com.unhappychoice.norimaki.presentation.core.scope.ViewScope
import com.unhappychoice.norimaki.presentation.screen.BuildStepScreen
import com.unhappychoice.norimaki.presentation.presenter.core.PresenterNeedsToken
import com.unhappychoice.norimaki.presentation.view.BuildView
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.switchLatest
import mortar.MortarScope
import javax.inject.Inject

@ViewScope
class BuildPresenter @Inject constructor(
    val build: Build,
    activity: MainActivity,
    api: CircleCIAPIClientV1,
    eventBus: EventBusService,
    pusher: PusherService
) : PresenterNeedsToken<BuildView>(activity, api, eventBus, pusher) {
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
            .withLog("updateAction")
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
        val stepIndex = steps.value.indexOf(buildStep)
        goTo(activity, BuildStepScreen(build, buildStep, stepIndex))
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