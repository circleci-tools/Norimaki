package com.unhappychoice.norimaki.presentation.presenter

import com.github.unhappychoice.circleci.v1.response.Build
import com.unhappychoice.norimaki.domain.model.addDistinctByNumber
import com.unhappychoice.norimaki.domain.model.sortByQueuedAt
import com.unhappychoice.norimaki.domain.model.vcsTypeFromUrl
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
    val builds = Variable<List<Build>>(listOf())

    override fun onEnterScope(scope: MortarScope?) {
        super.onEnterScope(scope)
        getBuilds()

        pusher.buildListUpdated
            .subscribeNext {
                getBuildsAPI(offset = 0)
                    .subscribeOnIoObserveOnUI()
                    .subscribeNext { builds.value = builds.value.addDistinctByNumber(it).sortByQueuedAt() }
            }.addTo(bag)

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
        if (isLoading.value || !hasMore.value) return
        getBuildsAPI(offset = this.page.value * 20)
            .startLoading()
            .paginate()
            .subscribeOnIoObserveOnUI()
            .subscribeNext { builds.value = builds.value.addDistinctByNumber(it).sortByQueuedAt() }
            .addTo(bag)
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
            "" -> api.getRecentBuilds(offset = offset, limit = 20)
            else -> api.getProjectBuilds(
                vcsType = vcsType(),
                userName = userName(),
                project = repoName(),
                offset = offset,
                limit = 20
            )
        }

    private fun vcsType(): String = projectName.split("/").let {
        if (it.size >= 3) it[0] else "github"
    }
    private fun userName(): String = projectName.split("/").let {
        if (it.size >= 3) it[1] else it[0]
    }
    private fun repoName(): String = projectName.split("/").last()
}
