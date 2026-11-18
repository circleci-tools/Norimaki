package com.unhappychoice.norimaki.presentation.presenter

import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.domain.model.WorkflowJob
import com.unhappychoice.norimaki.extension.Variable
import com.unhappychoice.norimaki.extension.goTo
import com.unhappychoice.norimaki.infrastructure.workflow.WorkflowClient
import com.unhappychoice.norimaki.infrastructure.workflow.WorkflowHttpException
import com.unhappychoice.norimaki.presentation.presenter.core.PresenterNeedsToken
import com.unhappychoice.norimaki.presentation.screen.BuildScreen
import com.unhappychoice.norimaki.presentation.screen.WorkflowScreen
import com.unhappychoice.norimaki.presentation.view.WorkflowView
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.addTo
import io.reactivex.schedulers.Schedulers
import org.kodein.di.instance

class WorkflowPresenter(val screen: WorkflowScreen) : PresenterNeedsToken<WorkflowView>() {
    private val client: WorkflowClient by instance()
    private val submittedApprovals = mutableSetOf<String>()
    private val state = Variable(WorkflowState())
    internal val updates = state.asObservable()

    fun refresh() = runRequest(client.jobs(screen.workflowId), R.string.workflow_load_error) { jobs ->
        state.value = state.value.copy(jobs = jobs)
    }

    fun canApprove(job: WorkflowJob): Boolean =
        job.pendingApprovalId?.let { it !in submittedApprovals } == true

    fun approve(job: WorkflowJob) {
        val requestId = job.pendingApprovalId ?: return
        if (!canApprove(job) || job !in state.value.jobs || state.value.busy) return
        val approval = client.approve(screen.workflowId, requestId)
            .andThen(Single.just(ApprovalResult()))
            .onErrorReturn { ApprovalResult(error = it) }
        val approvalWithRefresh = approval.flatMap { result ->
            client.jobs(screen.workflowId)
                .map { result.copy(jobs = it) }
                .onErrorReturnItem(result)
        }
        runRequest(approvalWithRefresh, R.string.workflow_approval_error) { result ->
            if (result.error == null) submittedApprovals.add(requestId)
            state.value = state.value.copy(
                jobs = result.jobs ?: state.value.jobs,
                message = approvalMessage(job, result),
                refreshFailed = result.jobs == null
            )
        }
    }

    fun openBuild(job: WorkflowJob) {
        val number = job.jobNumber ?: return
        val (vcs, owner, repo) = screen.projectName.split('/')
        runRequest(api.getBuild(vcs, owner, repo, number).firstOrError(), R.string.workflow_build_load_error) {
            goTo(activity, BuildScreen(it))
        }
    }

    private fun <T : Any> runRequest(request: Single<T>, errorMessage: Int, onSuccess: (T) -> Unit) {
        if (state.value.busy) return
        state.value = state.value.copy(busy = true, message = null, refreshFailed = false)
        request.subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doFinally { state.value = state.value.copy(busy = false) }
            .subscribe(onSuccess, { state.value = state.value.copy(message = errorMessage) })
            .addTo(bag)
    }

    private fun approvalMessage(job: WorkflowJob, result: ApprovalResult): Int = when {
        result.error == null -> R.string.workflow_approval_submitted
        (result.error as? WorkflowHttpException)?.status in listOf(401, 403) ->
            R.string.workflow_approval_permission
        result.jobs != null && result.jobs.none { it.id == job.id && it.pendingApprovalId == job.pendingApprovalId } ->
            R.string.workflow_approval_resolved
        else -> R.string.workflow_approval_error
    }

    private data class ApprovalResult(val jobs: List<WorkflowJob>? = null, val error: Throwable? = null)
}

internal data class WorkflowState(
    val jobs: List<WorkflowJob> = emptyList(),
    val busy: Boolean = false,
    val message: Int? = null,
    val refreshFailed: Boolean = false
)
