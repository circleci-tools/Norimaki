package com.unhappychoice.norimaki.domain.service

import com.unhappychoice.norimaki.domain.model.WorkflowJob
import io.reactivex.Completable
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.BehaviorSubject

internal class WorkflowApprovals {
    private val state = BehaviorSubject.createDefault(WorkflowApprovalState())
    val updates = state.hide()

    fun canApprove(requestId: String): Boolean = state.value!!.let {
        !it.pending && requestId !in it.submitted
    }

    fun isIdle(): Boolean = state.value == WorkflowApprovalState()

    fun submit(job: WorkflowJob, request: () -> Completable) {
        val requestId = job.pendingApprovalId ?: return
        synchronized(this) {
            if (!canApprove(requestId)) return
            state.onNext(state.value!!.copy(pending = true, outcome = null))
        }
        Completable.defer(request)
            .subscribeOn(Schedulers.io())
            .subscribe({ finish(job, requestId, null) }, { finish(job, requestId, it) })
    }

    @Synchronized
    private fun finish(job: WorkflowJob, requestId: String, error: Throwable?) {
        val submitted = state.value!!.submitted
        state.onNext(WorkflowApprovalState(
            submitted = if (error == null) submitted + requestId else submitted,
            outcome = WorkflowApprovalOutcome(job, error)
        ))
    }
}

internal data class WorkflowApprovalState(
    val submitted: Set<String> = emptySet(),
    val pending: Boolean = false,
    val outcome: WorkflowApprovalOutcome? = null
)

internal data class WorkflowApprovalOutcome(val job: WorkflowJob, val error: Throwable?)
