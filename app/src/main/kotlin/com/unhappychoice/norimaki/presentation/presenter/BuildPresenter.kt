package com.unhappychoice.norimaki.presentation.presenter

import com.github.unhappychoice.circleci.v1.response.Build
import com.github.unhappychoice.circleci.v1.response.BuildStep
import com.unhappychoice.norimaki.domain.model.BuildId
import com.unhappychoice.norimaki.domain.model.BuildStepId
import com.unhappychoice.norimaki.domain.model.isFinished
import com.unhappychoice.norimaki.domain.model.projectName
import com.unhappychoice.norimaki.extension.*
import com.unhappychoice.norimaki.presentation.presenter.core.PresenterNeedsToken
import com.unhappychoice.norimaki.presentation.screen.BuildStepScreen
import com.unhappychoice.norimaki.presentation.screen.WorkflowScreen
import com.unhappychoice.norimaki.presentation.view.BuildView
import io.reactivex.Observable
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import java.util.concurrent.TimeUnit
import org.kodein.di.instance
import java.util.concurrent.atomic.AtomicBoolean

class BuildPresenter : PresenterNeedsToken<BuildView>() {
    private val buildId: BuildId by instance()
    private var build: Build? = null
    val steps = Variable<List<BuildStep>>(listOf())
    val buildLoadFailed = Variable(false)
    private val rebuildInFlight = AtomicBoolean()

    fun pollBuild(onLoaded: (Build) -> Unit) {
        buildLoadFailed.value = false
        Observable.defer { buildId.load(api) }
            .concatWith(
                Observable.timer(BUILD_POLLING_SECONDS, TimeUnit.SECONDS)
                    .flatMap { polling(BUILD_POLLING_SECONDS) { buildId.load(api) } }
            )
            .takeUntil { it.isFinished() }
            .subscribeOnIoObserveOnUI()
            .subscribeBy(
                onNext = { loadedBuild ->
                    val hadWorkflow = hasWorkflow()
                    build = loadedBuild
                    onLoaded(loadedBuild)
                    val snapshot = loadedBuild.steps.orEmpty()
                    if (steps.value != snapshot) steps.value = snapshot
                    if (hasWorkflow() != hadWorkflow) activity.invalidateOptionsMenu()
                },
                onError = { buildLoadFailed.value = true }
            )
            .addTo(bag)
    }

    fun goToBuildStepScreen(stepId: BuildStepId) {
        goTo(activity, BuildStepScreen(buildId, stepId))
    }

    fun hasWorkflow(): Boolean = build?.workflows?.workflowId != null

    fun goToWorkflowScreen() {
        val build = build ?: return
        val workflowId = build.workflows?.workflowId ?: return
        goTo(activity, WorkflowScreen(build.projectName(), workflowId, build.workflows?.workflowName ?: "Workflow"))
    }

    fun rebuild() = submitRebuild { retryBuild() }

    fun rebuildWithoutCache() = submitRebuild {
        api.deleteCache(buildId.vcsType, buildId.owner, buildId.repository)
            .flatMap { retryBuild() }
    }

    private fun submitRebuild(request: () -> Observable<Build>) {
        if (!rebuildInFlight.compareAndSet(false, true)) return

        Observable.defer { request() }
            .subscribeOnIoObserveOnUI()
            .doFinally { rebuildInFlight.set(false) }
            .subscribeNext { goBack(activity) }
            .addTo(bag)
    }

    private fun retryBuild(): Observable<Build> =
        api.retryBuild(buildId.vcsType, buildId.owner, buildId.repository, buildId.number)

    companion object {
        const val BUILD_POLLING_SECONDS = 5L
    }
}
