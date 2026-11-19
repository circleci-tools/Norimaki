package com.unhappychoice.norimaki.presentation.presenter

import com.github.unhappychoice.circleci.CircleCIAPIClientV1_1
import com.github.unhappychoice.circleci.v1.response.Build
import com.github.unhappychoice.circleci.v1.response.BuildStep
import com.unhappychoice.norimaki.domain.model.isFinished
import com.unhappychoice.norimaki.domain.model.projectName
import com.unhappychoice.norimaki.domain.model.vcsType
import com.unhappychoice.norimaki.domain.service.rebuildWithoutCache
import com.unhappychoice.norimaki.extension.*
import com.unhappychoice.norimaki.presentation.presenter.core.PresenterNeedsToken
import com.unhappychoice.norimaki.presentation.screen.BuildStepScreen
import com.unhappychoice.norimaki.presentation.screen.WorkflowScreen
import com.unhappychoice.norimaki.presentation.view.BuildView
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import org.kodein.di.instance
import java.util.concurrent.atomic.AtomicBoolean

class BuildPresenter : PresenterNeedsToken<BuildView>() {
    val build: Build by instance()
    val steps = Variable<List<BuildStep>>(listOf())
    val rebuildFailed = Variable(false)
    private val rebuildInFlight = AtomicBoolean()

    override fun onAuthenticated() {
        pollBuild()
    }

    fun pollBuild() {
        polling(BUILD_POLLING_SECONDS) { getBuildAPI() }
            .takeUntil { it.isFinished() }
            .subscribeOnIoObserveOnUI()
            .subscribeNext { build ->
                val snapshot = build.steps.orEmpty()
                if (steps.value != snapshot) steps.value = snapshot
            }
            .addTo(bag)
    }

    fun goToBuildStepScreen(buildStep: BuildStep) {
        if (buildStep.actions.isEmpty()) return
        goTo(activity, BuildStepScreen(build, buildStep))
    }

    fun hasWorkflow(): Boolean = build.workflows?.workflowId != null

    fun goToWorkflowScreen() {
        val workflowId = build.workflows?.workflowId ?: return
        goTo(activity, WorkflowScreen(build.projectName(), workflowId, build.workflows?.workflowName ?: "Workflow"))
    }

    fun rebuild() {
        val client = account?.client ?: return
        if (!rebuildInFlight.compareAndSet(false, true)) return
        rebuildFailed.value = false

        Observable.defer { retryBuild(client) }
            .subscribeOnIoObserveOnUI()
            .doFinally { rebuildInFlight.set(false) }
            .subscribeBy(
                onError = {
                    it.printStackTrace()
                    rebuildFailed.value = true
                },
                onNext = { goBack(activity) }
            )
            .addTo(bag)
    }

    fun rebuildWithoutCache() {
        val verified = account ?: return
        val origin = view
        if (!rebuildInFlight.compareAndSet(false, true)) return
        rebuildFailed.value = false

        verified.client.rebuildWithoutCache(
            build,
            cancel = sessionEnded(verified),
            onFinished = { rebuildInFlight.set(false) }
        )
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeBy(
                onError = { rebuildFailed.value = true },
                onNext = { origin?.let(::goBackFrom) }
            )
            .addTo(bag)
    }

    private fun retryBuild(client: CircleCIAPIClientV1_1): Observable<Build> =
        client.retryBuild(build.vcsType(), build.username!!, build.reponame!!, build.buildNum!!)

    private fun goBackFrom(origin: BuildView) {
        val container = activity.binding.containerView
        if (origin.isAttachedToWindow && container.childCount == 1 && container.getChildAt(0) === origin) {
            goBack(activity)
        }
    }

    private fun getBuildAPI(): Observable<Build> =
        account?.client?.getBuild(build.vcsType(), build.username!!, build.reponame!!, build.buildNum!!)
            ?: Observable.empty()

    companion object {
        const val BUILD_POLLING_SECONDS = 5L
    }
}
