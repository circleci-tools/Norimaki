package com.unhappychoice.norimaki.presentation.screen

import com.github.unhappychoice.circleci.response.Build
import com.unhappychoice.norimaki.ActivityComponent
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.domain.model.addDistinctByNumber
import com.unhappychoice.norimaki.domain.model.sortByQueuedAt
import com.unhappychoice.norimaki.extension.*
import com.unhappychoice.norimaki.presentation.screen.core.Loadable
import com.unhappychoice.norimaki.presentation.screen.core.Paginatable
import com.unhappychoice.norimaki.presentation.screen.core.PresenterNeedsToken
import com.unhappychoice.norimaki.presentation.screen.core.Screen
import com.unhappychoice.norimaki.presentation.view.BuildListView
import com.unhappychoice.norimaki.scope.ViewScope
import dagger.Subcomponent
import io.reactivex.rxkotlin.addTo
import mortar.MortarScope
import javax.inject.Inject

class BuildListScreen : Screen() {
    override fun getLayoutResource() = R.layout.build_list_view
    override fun getSubComponent(activityComponent: ActivityComponent) = activityComponent.buildListScreenComponent()
    override fun getTitle(): String = "All builds"

    @Subcomponent @ViewScope interface Component {
        fun inject(view: BuildListView)
    }

    @ViewScope class Presenter @Inject constructor() : PresenterNeedsToken<BuildListView>(), Loadable, Paginatable {
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
}