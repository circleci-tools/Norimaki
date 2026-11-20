package com.unhappychoice.norimaki.presentation.presenter

import com.github.unhappychoice.circleci.v1.response.Build
import com.unhappychoice.norimaki.domain.model.BuildId
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
import io.reactivex.rxkotlin.addTo
import io.reactivex.schedulers.Schedulers
import org.kodein.di.instance
import org.kodein.di.instanceOrNull

class BuildListPresenter : PresenterNeedsToken<BuildListView>(), Loadable, Paginatable {
    val projectName: String by instance()
    private val workflowId: String? by instanceOrNull(tag = WorkflowScreen.WORKFLOW_ID_TAG)
    private val buildList by lazy {
        BuildListRequests(
            workflowId, Schedulers.io(), Schedulers.computation(), AndroidSchedulers.mainThread(),
            ::getBuildDetails, ::getBuildsAPI
        )
    }
    val isWorkflow: Boolean get() = !workflowId.isNullOrBlank()
    val builds get() = buildList.builds
    override val isLoading get() = buildList.isLoading
    override val page get() = buildList.page
    override val hasMore get() = buildList.hasMore

    override fun onAuthenticated() {
        buildList.poll(BUILD_LIST_POLLING_SECONDS).addTo(bag)
    }

    override fun onSessionEnded() {
        buildList.reset()
    }

    fun getBuilds() {
        if (account != null) buildList.loadNextPage()?.addTo(bag)
    }

    fun getBuildsIfUnderfilled(canScroll: Boolean) {
        if (isWorkflow && !canScroll) getBuilds()
    }

    fun goToBuildView(build: Build) {
        if (account == null) return
        val buildId = BuildId.from(build) ?: return
        goTo(activity, BuildScreen(buildId))
    }

    fun changeAPIToken() {
        goTo(activity, APITokenScreen())
    }

    internal fun getBuildDetails(build: Build): Observable<Build> {
        val buildId = BuildId.from(build) ?: return Observable.empty()
        return Observable.defer {
            val client = account?.client ?: return@defer Observable.empty<Build>()
            buildId.load(client)
        }
    }

    private fun getBuildsAPI(offset: Int): Observable<List<Build>> {
        val client = account?.client ?: return Observable.empty()
        return when (projectName) {
            "" -> client.getRecentBuilds(offset = offset, limit = BUILD_LIST_PAGE_SIZE)
            else -> client.getProjectBuilds(
                vcsType = vcsType(),
                userName = userName(),
                project = repoName(),
                offset = offset,
                limit = BUILD_LIST_PAGE_SIZE
            )
        }
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
