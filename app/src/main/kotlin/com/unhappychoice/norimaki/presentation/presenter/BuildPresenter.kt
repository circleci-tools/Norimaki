package com.unhappychoice.norimaki.presentation.presenter

import com.github.unhappychoice.circleci.v1.response.Build
import com.github.unhappychoice.circleci.v1.response.BuildStep
import com.unhappychoice.norimaki.domain.model.BuildId
import com.unhappychoice.norimaki.domain.model.BuildStatus
import com.unhappychoice.norimaki.domain.model.BuildStepId
import com.unhappychoice.norimaki.domain.model.ProjectId
import com.unhappychoice.norimaki.domain.model.buildStatus
import com.unhappychoice.norimaki.domain.model.isFinished
import com.unhappychoice.norimaki.domain.model.projectId
import com.unhappychoice.norimaki.extension.*
import com.unhappychoice.norimaki.presentation.presenter.core.PresenterNeedsToken
import com.unhappychoice.norimaki.presentation.presenter.core.StartedPolling
import com.unhappychoice.norimaki.presentation.screen.ArtifactsScreen
import com.unhappychoice.norimaki.presentation.screen.BuildStepScreen
import com.unhappychoice.norimaki.presentation.screen.TestResultsScreen
import com.unhappychoice.norimaki.presentation.screen.WorkflowScreen
import com.unhappychoice.norimaki.presentation.view.BuildView
import io.reactivex.Observable
import io.reactivex.disposables.Disposable
import io.reactivex.disposables.Disposables
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import java.util.concurrent.TimeUnit
import org.kodein.di.instance

class BuildPresenter : PresenterNeedsToken<BuildView>() {
    private val buildId: BuildId by instance()
    private var build: Build? = null
    val projectId: ProjectId
        get() = buildId.projectId
    val steps = Variable<List<BuildStep>>(listOf())
    val buildLoadFailed = Variable(false)
    val rebuildFailed = Variable(false)
    val cancelFailed = Variable(false)

    private var buildPolling: StartedPolling? = null

    override fun onAuthenticated() {
        buildPolling = StartedPolling(shouldSkip = { build?.isFinished() == true }, subscribe = ::subscribeToBuild)
            .also { it.observe(activity.lifecycle, bag) }
    }

    fun pollBuild() {
        buildPolling?.refresh()
    }

    private fun subscribeToBuild(): Disposable {
        val client = account?.client ?: return Disposables.disposed()
        buildLoadFailed.value = false
        return Observable.defer { buildId.load(client) }
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
    }

    fun goToBuildStepScreen(stepId: BuildStepId) {
        if (account == null) return
        goTo(activity, BuildStepScreen(buildId, stepId))
    }

    fun goToTestResultsScreen() {
        if (build == null) return
        goTo(activity, TestResultsScreen(buildId))
    }

    fun goToArtifactsScreen() {
        if (account == null) return
        goTo(activity, ArtifactsScreen(buildId))
    }

    fun hasWorkflow(): Boolean = build?.nonBlankWorkflowId != null

    fun isActive(): Boolean = build?.buildStatus() == BuildStatus.ACTIVE

    fun goToWorkflowScreen() {
        val build = build ?: return
        val workflowId = build.nonBlankWorkflowId ?: return
        goTo(activity, WorkflowScreen(build.projectId(), workflowId, build.workflows?.workflowName ?: "Workflow"))
    }

    fun rebuild() = submitRebuild(clearCache = false)

    fun rebuildWithoutCache() = submitRebuild(clearCache = true)

    private fun submitRebuild(clearCache: Boolean) {
        val verified = account ?: return
        val origin = view
        val result = verified.startRebuild(buildId, clearCache, sessionEnded(verified)) ?: return
        rebuildFailed.value = false

        result
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeBy(
                onError = { rebuildFailed.value = true },
                onNext = { origin?.let(::goBackFrom) }
            )
            .addTo(bag)
    }

    fun cancel() {
        val verified = account ?: return
        val result = verified.startCancel(buildId, sessionEnded(verified)) ?: return
        cancelFailed.value = false

        result
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeBy(
                onError = { cancelFailed.value = true },
                onNext = { pollBuild() }
            )
            .addTo(bag)
    }

    private val Build.nonBlankWorkflowId: String?
        get() = workflows?.workflowId?.takeUnless { it.isBlank() }

    private fun goBackFrom(origin: BuildView) {
        if (origin.isCurrentlyDisplayed()) {
            goBack(activity)
        }
    }

    companion object {
        const val BUILD_POLLING_SECONDS = 5L
    }
}
