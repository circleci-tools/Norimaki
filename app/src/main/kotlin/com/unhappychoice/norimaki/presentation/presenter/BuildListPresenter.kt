package com.unhappychoice.norimaki.presentation.presenter

import com.github.unhappychoice.circleci.CircleCIAPIClientV1
import com.github.unhappychoice.circleci.response.Build
import com.unhappychoice.norimaki.MainActivity
import com.unhappychoice.norimaki.domain.model.addDistinctByNumber
import com.unhappychoice.norimaki.domain.model.sortByQueuedAt
import com.unhappychoice.norimaki.domain.service.EventBusService
import com.unhappychoice.norimaki.extension.*
import com.unhappychoice.norimaki.infrastructure.pusher.PusherService
import com.unhappychoice.norimaki.presentation.screen.APITokenScreen
import com.unhappychoice.norimaki.presentation.screen.BuildScreen
import com.unhappychoice.norimaki.presentation.screen.core.Loadable
import com.unhappychoice.norimaki.presentation.screen.core.Paginatable
import com.unhappychoice.norimaki.presentation.screen.core.PresenterNeedsToken
import com.unhappychoice.norimaki.presentation.view.BuildListView
import io.reactivex.rxkotlin.addTo
import mortar.MortarScope

class BuildListPresenter(
    activity: MainActivity,
    api: CircleCIAPIClientV1,
    eventBus: EventBusService,
    pusher: PusherService
) : PresenterNeedsToken<BuildListView>(activity, api, eventBus, pusher), Loadable, Paginatable {
    override val isLoading = Variable(false)
    override val page = Variable(0)
    override val hasMore = Variable(true)
    val builds = Variable<List<Build>>(listOf())

    override fun onEnterScope(scope: MortarScope?) {
        super.onEnterScope(scope)
        getBuilds()

        eventBus.buildListUpdated
            .withLog("buildListUpdated")
            .subscribeNext {
                api.getRecentBuilds(offset = 0, limit = 20)
                    .subscribeOnIoObserveOnUI()
                    .subscribeNext { builds.value = builds.value.addDistinctByNumber(it).sortByQueuedAt() }
            }.addTo(bag)
    }

    fun getBuilds() {
        if (isLoading.value || !hasMore.value) return
        api.getRecentBuilds(offset = this.page.value * 20, limit = 20)
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
}