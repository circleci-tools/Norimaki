package com.unhappychoice.norimaki.presentation.screen

import android.util.Log
import com.github.unhappychoice.circleci.response.Build
import com.unhappychoice.norimaki.ActivityComponent
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.extension.*
import com.unhappychoice.norimaki.presentation.screen.core.Loadable
import com.unhappychoice.norimaki.presentation.screen.core.Paginatable
import com.unhappychoice.norimaki.presentation.screen.core.PresenterNeedsToken
import com.unhappychoice.norimaki.presentation.screen.core.Screen
import com.unhappychoice.norimaki.presentation.view.BuildListView
import com.unhappychoice.norimaki.scope.ViewScope
import dagger.Subcomponent
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import mortar.MortarScope
import javax.inject.Inject

class BuildListScreen : Screen() {
  override fun getLayoutResource() = R.layout.build_list_view
  override fun getSubComponent(activityComponent: ActivityComponent) = activityComponent.buildListScreenComponent()

  @Subcomponent @ViewScope interface Component {
    fun inject(view: BuildListView)
  }

  @ViewScope class Presenter @Inject constructor() : PresenterNeedsToken<BuildListView>(), Loadable, Paginatable {
    override val isLoading = Variable(false)
    override val page = Variable(0)
    override val hasMore = Variable(true)
    val builds = Variable<List<Build>>(listOf())
    private val bag = CompositeDisposable()

    override fun onEnterScope(scope: MortarScope?) {
      super.onEnterScope(scope)
      getBuilds()
    }

    override fun onExitScope() {
      bag.dispose()
      super.onExitScope()
    }

    fun getBuilds() {
      if (isLoading.value || !hasMore.value) return
      api.client().getRecentBuilds(page.value * 20)
        .startLoading()
        .paginate()
        .subscribeOnIoObserveOnUI()
        .subscribeNext { builds.value = builds.value + it }
        .addTo(bag)
    }

    fun goToBuildView(build: Build) {
      goTo(activity, BuildScreen(build))
    }
  }
}