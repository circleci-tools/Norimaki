package com.unhappychoice.norimaki.presentation.presenter

import com.github.unhappychoice.circleci.v2.response.Job
import com.github.unhappychoice.circleci.v2.response.Workflow
import com.unhappychoice.norimaki.extension.Variable
import com.unhappychoice.norimaki.extension.subscribeNext
import com.unhappychoice.norimaki.extension.subscribeOnIoObserveOnUI
import com.unhappychoice.norimaki.presentation.presenter.core.PresenterNeedsToken
import com.unhappychoice.norimaki.presentation.view.BuildStepView
import io.reactivex.rxkotlin.addTo
import mortar.MortarScope
import org.kodein.di.instance

class BuildStepPresenter: PresenterNeedsToken<BuildStepView>() {
    val workflow: Workflow by instance()
    val job: Job by instance()

    val logString: Variable<String> = Variable("")

    override fun onEnterScope(scope: MortarScope?) {
        super.onEnterScope(scope)
        getJobDetails()
    }

    private fun getJobDetails() {
        val projectSlug = workflow.projectSlug
        api.getJobDetails(projectSlug, job.id)
            .subscribeOnIoObserveOnUI()
            .subscribeNext { logString.value = "Job: ${it.name}\nStatus: ${job.status}" }
            .addTo(bag)
    }
}
