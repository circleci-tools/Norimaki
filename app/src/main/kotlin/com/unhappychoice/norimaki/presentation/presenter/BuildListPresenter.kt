package com.unhappychoice.norimaki.presentation.presenter

import com.github.unhappychoice.circleci.v1.response.Build
import com.unhappychoice.norimaki.domain.model.vcsTypeFromUrl
import com.unhappychoice.norimaki.extension.goTo
import com.unhappychoice.norimaki.extension.subscribeNext
import com.unhappychoice.norimaki.presentation.presenter.core.PresenterNeedsToken
import com.unhappychoice.norimaki.presentation.screen.APITokenScreen
import com.unhappychoice.norimaki.presentation.screen.BuildListScreen
import com.unhappychoice.norimaki.presentation.screen.BuildScreen
import com.unhappychoice.norimaki.presentation.screen.WorkflowScreen
import com.unhappychoice.norimaki.presentation.view.BuildListView
import flow.Direction
import flow.Flow
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import io.reactivex.rxkotlin.addTo
import mortar.MortarScope
import org.kodein.di.instance
import org.kodein.di.instanceOrNull

class BuildListPresenter : PresenterNeedsToken<BuildListView>() {
    val projectName: String by instance()
    private val workflowId: String? by instanceOrNull(tag = WorkflowScreen.WORKFLOW_ID_TAG)
    private val buildList by lazy {
        BuildListRequests(workflowId, Schedulers.io(), AndroidSchedulers.mainThread(), ::getBuildsAPI)
    }
    val builds get() = buildList.builds

    override fun onEnterScope(scope: MortarScope?) {
        super.onEnterScope(scope)
        buildList.poll(BUILD_LIST_POLLING_SECONDS).addTo(bag)

        eventBus.selectProject
            .map { optional ->
                val project = optional.toNullable()
                    ?: return@map ""
                "${vcsTypeFromUrl(project.vcsUrl)}/${project.username}/${project.reponame}"
            }
            .subscribeNext { goToBuildListView(it) }
            .addTo(bag)
    }

    fun getBuilds() {
        buildList.loadNextPage()?.addTo(bag)
    }

    fun goToBuildView(build: Build) {
        goTo(activity, BuildScreen(build))
    }

    fun changeAPIToken() {
        goTo(activity, APITokenScreen())
    }

    private fun goToBuildListView(projectName: String) {
        Flow.get(activity).replaceTop(BuildListScreen(projectName), Direction.REPLACE)
    }

    private fun getBuildsAPI(offset: Int): Observable<List<Build>> =
        when (projectName) {
            "" -> api.getRecentBuilds(offset = offset, limit = BuildListRequests.PAGE_SIZE)
            else -> api.getProjectBuilds(
                vcsType = vcsType(),
                userName = userName(),
                project = repoName(),
                offset = offset,
                limit = BuildListRequests.PAGE_SIZE
            )
        }

    private fun vcsType(): String = projectName.split("/").let {
        if (it.size >= 3) it[0] else "github"
    }
    private fun userName(): String = projectName.split("/").let {
        if (it.size >= 3) it[1] else it[0]
    }
    private fun repoName(): String = projectName.split("/").last()

    companion object {
        const val BUILD_LIST_POLLING_SECONDS = 10L
    }
}
