package com.unhappychoice.norimaki.presentation.screen

import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.presentation.presenter.WorkflowPresenter
import com.unhappychoice.norimaki.presentation.screen.core.Screen
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.provider

class WorkflowScreen(val projectName: String, val workflowId: String, val workflowName: String) : Screen() {
    override fun getTitle(): String = workflowName
    override fun getLayoutResource() = R.layout.workflow_view

    override fun module(activityModule: DI) = DI {
        extend(activityModule)
        bind<WorkflowPresenter>() with provider { WorkflowPresenter(this@WorkflowScreen) }
    }

    override fun equals(other: Any?): Boolean =
        (other as? WorkflowScreen)?.workflowId == workflowId

    override fun hashCode(): Int = workflowId.hashCode()
}
