package com.unhappychoice.norimaki.presentation.presenter

import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.domain.model.BuildId
import com.unhappychoice.norimaki.domain.model.WorkflowJob
import com.unhappychoice.norimaki.domain.service.VerifiedSession
import com.unhappychoice.norimaki.domain.service.WorkflowApprovalOutcome
import com.unhappychoice.norimaki.domain.service.WorkflowApprovals
import com.unhappychoice.norimaki.extension.Variable
import com.unhappychoice.norimaki.extension.androidMainScheduler
import com.unhappychoice.norimaki.extension.goTo
import com.unhappychoice.norimaki.infrastructure.workflow.WorkflowClient
import com.unhappychoice.norimaki.infrastructure.workflow.WorkflowHttpException
import com.unhappychoice.norimaki.presentation.presenter.core.PresenterNeedsToken
import com.unhappychoice.norimaki.presentation.screen.APITokenScreen
import com.unhappychoice.norimaki.presentation.screen.BuildScreen
import com.unhappychoice.norimaki.presentation.screen.WorkflowScreen
import com.unhappychoice.norimaki.presentation.view.WorkflowView
import io.reactivex.disposables.SerialDisposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.schedulers.Schedulers

class WorkflowPresenter(
    val screen: WorkflowScreen,
    private val createClient: (String) -> WorkflowClient = { WorkflowClient(it) }
) : PresenterNeedsToken<WorkflowView>() {
    private var approvals: WorkflowApprovals? = null
    private var pendingOutcome: WorkflowApprovalOutcome? = null
    private var approvalsSession: VerifiedSession? = null
    private lateinit var client: WorkflowClient
    private lateinit var jobsRequest: SerialDisposable
    private val state = Variable(WorkflowState())
    internal val updates = state.asObservable()

    override fun onAuthenticated() {
        val verified = account ?: return
        approvalsSession = verified
        val workflowApprovals = verified.workflowApprovals(screen.workflowId)
        approvals = workflowApprovals
        client = createClient(verified.token)
        jobsRequest = SerialDisposable().also { it.addTo(bag) }
        workflowApprovals.updates
            .observeOn(androidMainScheduler())
            .subscribe { approval ->
                if (account !== verified) return@subscribe
                if (approval.pending) {
                    jobsRequest.set(null)
                    state.value = state.value.copy(busy = true, message = null, refreshFailed = false)
                } else {
                    loadJobs(approval.outcome)
                }
            }
            .addTo(bag)
    }

    override fun onSessionEnded() {
        approvalsSession?.releaseWorkflowApprovals(screen.workflowId)
        approvals = null
        state.value = WorkflowState()
    }

    override fun onViewDropped() {
        approvalsSession?.releaseWorkflowApprovals(screen.workflowId)
    }

    fun refresh() {
        if (account == null || state.value.busy) return
        loadJobs(pendingOutcome)
    }

    fun canApprove(job: WorkflowJob): Boolean =
        account != null && job.pendingApprovalId?.let { approvals?.canApprove(it) } == true

    fun approve(job: WorkflowJob) {
        val requestId = job.pendingApprovalId ?: return
        if (!canApprove(job) || job !in state.value.jobs || state.value.busy) return
        val client = this.client
        val workflowId = screen.workflowId
        approvals?.submit(job) { client.approve(workflowId, requestId) }
    }

    fun openBuild(job: WorkflowJob) {
        if (account == null || state.value.busy) return
        val number = job.jobNumber ?: return
        goTo(activity, BuildScreen(BuildId(screen.projectId, number)))
    }

    fun changeAPIToken() {
        goTo(activity, APITokenScreen())
    }

    private fun loadJobs(outcome: WorkflowApprovalOutcome? = null) {
        pendingOutcome = outcome
        jobsRequest.set(null)
        state.value = state.value.copy(busy = true, message = outcome?.let { approvalMessage(it) }, refreshFailed = false)
        val request = client.jobs(screen.workflowId)
            .subscribeOn(Schedulers.io())
            .observeOn(androidMainScheduler())
            .subscribe({ jobs ->
                pendingOutcome = null
                state.value = WorkflowState(jobs, message = outcome?.let { approvalMessage(it, jobs) })
            }, {
                state.value = state.value.copy(
                    busy = false,
                    message = outcome?.let { approvalMessage(it) } ?: R.string.workflow_load_error,
                    refreshFailed = outcome != null
                )
            })
        jobsRequest.set(request)
    }

    private fun approvalMessage(outcome: WorkflowApprovalOutcome, jobs: List<WorkflowJob>? = null): Int = when {
        outcome.error == null -> R.string.workflow_approval_submitted
        (outcome.error as? WorkflowHttpException)?.status in listOf(401, 403) ->
            R.string.workflow_approval_permission
        jobs != null && jobs.none {
            it.id == outcome.job.id && it.pendingApprovalId == outcome.job.pendingApprovalId
        } -> R.string.workflow_approval_resolved
        else -> R.string.workflow_approval_error
    }
}

internal data class WorkflowState(
    val jobs: List<WorkflowJob> = emptyList(),
    val busy: Boolean = false,
    val message: Int? = null,
    val refreshFailed: Boolean = false
)
