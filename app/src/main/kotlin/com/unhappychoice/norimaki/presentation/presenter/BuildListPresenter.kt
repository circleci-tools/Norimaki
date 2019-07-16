package com.unhappychoice.norimaki.presentation.presenter

import com.github.unhappychoice.circleci.response.Build
import com.github.unhappychoice.circleci.response.Project
import com.gojuno.koptional.None
import com.gojuno.koptional.Optional
import com.gojuno.koptional.Some
import com.unhappychoice.norimaki.domain.model.addDistinctByNumber
import com.unhappychoice.norimaki.domain.model.sortByQueuedAt
import com.unhappychoice.norimaki.extension.Variable
import com.unhappychoice.norimaki.extension.goTo
import com.unhappychoice.norimaki.extension.subscribeNext
import com.unhappychoice.norimaki.extension.subscribeOnIoObserveOnUI
import com.unhappychoice.norimaki.presentation.presenter.core.Loadable
import com.unhappychoice.norimaki.presentation.presenter.core.Paginatable
import com.unhappychoice.norimaki.presentation.presenter.core.PresenterNeedsToken
import com.unhappychoice.norimaki.presentation.screen.APITokenScreen
import com.unhappychoice.norimaki.presentation.screen.BuildScreen
import com.unhappychoice.norimaki.presentation.view.BuildListView
import io.reactivex.Observable
import io.reactivex.rxkotlin.addTo
import mortar.MortarScope

class BuildListPresenter: PresenterNeedsToken<BuildListView>(), Loadable, Paginatable {
    override val isLoading = Variable(false)
    override val page = Variable(0)
    override val hasMore = Variable(true)
    val builds = Variable<List<Build>>(listOf())
    private val project = Variable<Optional<Project>>(None)

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
            .doOnNext { project.value = it }
            .doOnNext { reset() }
            .subscribeNext { getBuilds() }
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

    private fun getBuildsAPI(offset: Int): Observable<List<Build>> =
        project.value.toNullable().let {
            when (it) {
                null -> api.getRecentBuilds(offset = offset, limit = 20)
                else -> api.getProjectBuilds(userName = it.username ?: "", project = it.reponame ?: "", offset = offset, limit = 20)
            }
        }

    private fun reset() {
        builds.value = emptyList()
        isLoading.value = false
        page.value = 0
        hasMore.value = true
    }
}