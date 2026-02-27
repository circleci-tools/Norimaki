package com.unhappychoice.norimaki.presentation.presenter

import com.github.unhappychoice.circleci.v2.response.Job
import com.github.unhappychoice.circleci.v2.response.Workflow
import com.unhappychoice.norimaki.extension.*
import com.unhappychoice.norimaki.presentation.presenter.core.PresenterNeedsToken
import com.unhappychoice.norimaki.presentation.view.BuildView
import io.reactivex.rxkotlin.addTo
import mortar.MortarScope
import org.kodein.di.instance

class BuildPresenter : PresenterNeedsToken<BuildView>() {
    val workflow: Workflow by instance()
    val jobs = Variable<List<Job>>(listOf())

    override fun onEnterScope(scope: MortarScope?) {
        super.onEnterScope(scope)
        getJobs()
    }

    fun getJobs() {
        api.getWorkflowJobs(workflow.id)
            .map { it.items }
            .subscribeOnIoObserveOnUI()
            .subscribeNext { jobs.value = it }
            .addTo(bag)
    }

    fun goToJobDetail(job: Job) {
        // Job detail view is not available in V2 API
    }

    fun rebuild() {
        api.rerunWorkflow(workflow.id, null)
            .subscribeOnIoObserveOnUI()
            .subscribeNext { goBack(activity) }
            .addTo(bag)
    }
}
