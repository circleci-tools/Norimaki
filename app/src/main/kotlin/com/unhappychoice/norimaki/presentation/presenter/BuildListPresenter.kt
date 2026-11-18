package com.unhappychoice.norimaki.presentation.presenter

import com.github.unhappychoice.circleci.v1.response.Build
import com.unhappychoice.norimaki.extension.goTo
import com.unhappychoice.norimaki.presentation.presenter.core.Loadable
import com.unhappychoice.norimaki.presentation.presenter.core.Paginatable
import com.unhappychoice.norimaki.presentation.presenter.core.PresenterNeedsToken
import com.unhappychoice.norimaki.presentation.screen.APITokenScreen
import com.unhappychoice.norimaki.presentation.screen.BuildScreen
import com.unhappychoice.norimaki.presentation.screen.WorkflowScreen
import com.unhappychoice.norimaki.presentation.view.BuildListView
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import io.reactivex.rxkotlin.addTo
import mortar.MortarScope
import org.kodein.di.instance
import org.kodein.di.instanceOrNull

class BuildListPresenter : PresenterNeedsToken<BuildListView>(), Loadable, Paginatable {
    val projectName: String by instance()
    private val workflowId: String? by instanceOrNull(tag = WorkflowScreen.WORKFLOW_ID_TAG)
    private val buildList by lazy {
        BuildListRequests(workflowId, Schedulers.io(), AndroidSchedulers.mainThread(), ::getBuildsAPI)
    }
    val isWorkflow: Boolean get() = !workflowId.isNullOrBlank()
    val builds get() = buildList.builds
    override val isLoading get() = buildList.isLoading
    override val page get() = buildList.page
    override val hasMore get() = buildList.hasMore

    override fun onEnterScope(scope: MortarScope?) {
        super.onEnterScope(scope)
        pollLatestBuilds()
    }

    fun getBuilds() {
        buildList.loadNextPage()?.addTo(bag)
    }

    fun getBuildsIfUnderfilled(canScroll: Boolean) {
        if (isWorkflow && !canScroll) getBuilds()
    }

    internal fun pollLatestBuilds() {
        buildList.poll(BUILD_LIST_POLLING_SECONDS).addTo(bag)
    }

    fun goToBuildView(build: Build) {
        goTo(activity, BuildScreen(build))
    }

    fun changeAPIToken() {
        goTo(activity, APITokenScreen())
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
