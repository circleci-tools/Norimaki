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
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.switchLatest
import org.kodein.di.instance

class BuildPresenter : PresenterNeedsToken<BuildView>() {
    private val buildId: BuildId by instance()
    private var build: Build? = null
    val steps = Variable<List<BuildStep>>(listOf())

    fun pollBuild(onLoaded: (Build) -> Unit) {
        polling(BUILD_POLLING_SECONDS) { buildId.load(api) }
            .takeUntil { it.isFinished() }
            .subscribeOnIoObserveOnUI()
            .subscribeNext {
                val hadWorkflow = hasWorkflow()
                build = it
                onLoaded(it)
                steps.value = it.steps.orEmpty()
                if (hasWorkflow() != hadWorkflow) activity.invalidateOptionsMenu()
            }
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

    fun rebuild() {
        api.retryBuild(buildId.vcsType, buildId.owner, buildId.repository, buildId.number)
            .subscribeOnIoObserveOnUI()
            .subscribeNext { goBack(activity) }
            .addTo(bag)
    }

    fun rebuildWithoutCache() {
        api.deleteCache(buildId.vcsType, buildId.owner, buildId.repository)
            .map {
                api.retryBuild(buildId.vcsType, buildId.owner, buildId.repository, buildId.number).subscribeOnIoObserveOnUI()
            }.switchLatest()
            .subscribeOnIoObserveOnUI()
            .subscribeNext { goBack(activity) }
            .addTo(bag)
    }

    companion object {
        const val BUILD_POLLING_SECONDS = 5L
    }
}
