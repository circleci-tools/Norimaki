package com.unhappychoice.norimaki.presentation.presenter

import com.github.unhappychoice.circleci.CircleCIAPIClientV1_1
import com.github.unhappychoice.circleci.v1.response.Build
import com.github.unhappychoice.circleci.v1.response.BuildStep
import com.unhappychoice.norimaki.domain.model.BuildId
import com.unhappychoice.norimaki.domain.model.BuildStepId
import com.unhappychoice.norimaki.domain.model.isFinished
import com.unhappychoice.norimaki.domain.model.projectName
import com.unhappychoice.norimaki.domain.service.rebuildWithoutCache
import com.unhappychoice.norimaki.extension.*
import com.unhappychoice.norimaki.presentation.presenter.core.PresenterNeedsToken
import com.unhappychoice.norimaki.presentation.screen.ArtifactsScreen
import com.unhappychoice.norimaki.presentation.screen.BuildStepScreen
import com.unhappychoice.norimaki.presentation.screen.WorkflowScreen
import com.unhappychoice.norimaki.presentation.view.BuildView
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
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

    override fun onAuthenticated() = pollBuild()

    fun pollBuild() {
        val client = account?.client ?: return
        buildLoadFailed.value = false
        Observable.defer { buildId.load(client) }
            .concatWith(
                Observable.timer(BUILD_POLLING_SECONDS, TimeUnit.SECONDS)
                    .flatMap { polling(BUILD_POLLING_SECONDS) { buildId.load(client) } }
            )
            .takeUntil { it.isFinished() }
            .subscribeOnIoObserveOnUI()
            .subscribeBy(
                onNext = { loadedBuild ->
                    val hadWorkflow = hasWorkflow()
                    build = loadedBuild
                    view?.showBuild(loadedBuild)
                    val snapshot = loadedBuild.steps.orEmpty()
                    if (steps.value != snapshot) steps.value = snapshot
                    if (hasWorkflow() != hadWorkflow) activity.invalidateOptionsMenu()
                },
                onError = { buildLoadFailed.value = true }
            )
            .addTo(bag)
    }

    fun goToBuildStepScreen(stepId: BuildStepId) {
        if (account == null) return
        goTo(activity, BuildStepScreen(buildId, stepId))
    }

    fun goToArtifactsScreen() {
        if (account == null) return
        goTo(activity, ArtifactsScreen(buildId))
    }

    fun hasWorkflow(): Boolean = build?.workflows?.workflowId != null

    fun goToWorkflowScreen() {
        val build = build ?: return
        val workflowId = build.workflows?.workflowId ?: return
        goTo(activity, WorkflowScreen(build.projectName(), workflowId, build.workflows?.workflowName ?: "Workflow"))
    }

    fun rebuild() {
        val client = account?.client ?: return
        if (!rebuildInFlight.compareAndSet(false, true)) return

        Observable.defer { retryBuild(client) }
            .subscribeOnIoObserveOnUI()
            .doFinally { rebuildInFlight.set(false) }
            .subscribeNext { goBack(activity) }
            .addTo(bag)
    }

    fun rebuildWithoutCache() {
        val verified = account ?: return
        val origin = view
        if (!rebuildInFlight.compareAndSet(false, true)) return

        verified.client.rebuildWithoutCache(
            buildId,
            cancel = sessionEnded(verified),
            onFinished = { rebuildInFlight.set(false) }
        )
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeBy(onError = {}, onNext = { origin?.let(::goBackFrom) })
            .addTo(bag)
    }

    private fun retryBuild(client: CircleCIAPIClientV1_1): Observable<Build> =
        client.retryBuild(buildId.vcsType, buildId.owner, buildId.repository, buildId.number)

    private fun goBackFrom(origin: BuildView) {
        val container = activity.binding.containerView
        if (origin.isAttachedToWindow && container.childCount == 1 && container.getChildAt(0) === origin) {
            goBack(activity)
        }
    }

    companion object {
        const val BUILD_POLLING_SECONDS = 5L
    }
}
