package com.unhappychoice.norimaki.presentation.screen

import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.presentation.screen.core.Screen
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.singleton

class WorkflowScreen(val projectName: String, val workflowId: String, val workflowName: String) : Screen() {
    override fun getTitle(): String = workflowName
    override fun getLayoutResource() = R.layout.build_list_view

    override fun module(activityModule: DI) = DI {
        extend(activityModule)
        bind<String>() with singleton { projectName }
        bind<String>(tag = WORKFLOW_ID_TAG) with singleton { workflowId }
    }

    override fun equals(other: Any?): Boolean =
        (other as? WorkflowScreen)?.workflowId == workflowId

    override fun hashCode(): Int = workflowId.hashCode()

    companion object {
        const val WORKFLOW_ID_TAG = "workflowId"
    }
}
