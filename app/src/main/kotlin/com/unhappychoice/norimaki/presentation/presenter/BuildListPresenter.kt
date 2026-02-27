package com.unhappychoice.norimaki.presentation.presenter

import com.github.unhappychoice.circleci.v2.response.Collaboration
import com.github.unhappychoice.circleci.v2.response.Workflow
import com.unhappychoice.norimaki.domain.model.addDistinctById
import com.unhappychoice.norimaki.domain.model.sortByCreatedAt
import com.unhappychoice.norimaki.extension.Variable
import com.unhappychoice.norimaki.extension.goTo
import com.unhappychoice.norimaki.extension.subscribeNext
import com.unhappychoice.norimaki.extension.subscribeOnIoObserveOnUI
import com.unhappychoice.norimaki.presentation.presenter.core.Loadable
import com.unhappychoice.norimaki.presentation.presenter.core.Paginatable
import com.unhappychoice.norimaki.presentation.presenter.core.PresenterNeedsToken
import com.unhappychoice.norimaki.presentation.screen.APITokenScreen
import com.unhappychoice.norimaki.presentation.screen.BuildListScreen
import com.unhappychoice.norimaki.presentation.screen.BuildScreen
import com.unhappychoice.norimaki.presentation.view.BuildListView
import flow.Direction
import flow.Flow
import io.reactivex.Observable
import io.reactivex.rxkotlin.addTo
import mortar.MortarScope
import org.kodein.di.instance

class BuildListPresenter: PresenterNeedsToken<BuildListView>(), Loadable, Paginatable {
    override val isLoading = Variable(false)
    override val page = Variable(0)
    override val hasMore = Variable(true)
    val projectName: String by instance()
    val builds = Variable<List<Workflow>>(listOf())

    private var nextPageToken: String? = null

    override fun onEnterScope(scope: MortarScope?) {
        super.onEnterScope(scope)
        getBuilds()

        eventBus.selectProject
            .map { optional ->
                optional.toNullable()?.let { "${it.vcsType}/${it.slug}" } ?: ""
            }
            .subscribeNext { goToBuildListView(it) }
            .addTo(bag)
    }

    fun getBuilds() {
        if (isLoading.value || !hasMore.value) return
        getWorkflowsFromPipelines()
            .startLoading()
            .subscribeOnIoObserveOnUI()
            .subscribeNext { builds.value = builds.value.addDistinctById(it).sortByCreatedAt() }
            .addTo(bag)
    }

    fun goToBuildView(workflow: Workflow) {
        goTo(activity, BuildScreen(workflow))
    }

    fun changeAPIToken() {
        goTo(activity, APITokenScreen())
    }

    private fun goToBuildListView(projectName: String) {
        Flow.get(activity).replaceTop(BuildListScreen(projectName), Direction.REPLACE)
    }

    private fun getWorkflowsFromPipelines(): Observable<List<Workflow>> {
        val pipelinesObservable = when (projectName) {
            "" -> api.listPipelines(mine = true, pageToken = nextPageToken)
            else -> api.getProjectPipelines(projectName, pageToken = nextPageToken)
        }

        return pipelinesObservable.flatMap { response ->
            nextPageToken = response.nextPageToken
            if (response.nextPageToken == null) hasMore.value = false
            isLoading.value = false

            val workflowObservables = response.items.map { pipeline ->
                api.getPipelineWorkflows(pipeline.id)
                    .map { it.items }
                    .onErrorReturnItem(emptyList())
            }
            if (workflowObservables.isEmpty()) Observable.just(emptyList())
            else Observable.zip(workflowObservables) { results ->
                results.flatMap {
                    @Suppress("UNCHECKED_CAST")
                    it as List<Workflow>
                }
            }
        }
    }
}
