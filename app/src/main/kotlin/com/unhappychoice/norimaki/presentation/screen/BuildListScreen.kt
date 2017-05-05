package com.unhappychoice.norimaki.presentation.screen

import com.github.unhappychoice.circleci.CircleCIAPIClient
import com.github.unhappychoice.circleci.response.Build
import com.unhappychoice.norimaki.ActivityComponent
import com.unhappychoice.norimaki.MainActivity
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.extension.*
import com.unhappychoice.norimaki.preference.APITokenPreference
import com.unhappychoice.norimaki.presentation.screen.core.Screen
import com.unhappychoice.norimaki.presentation.view.BuildListView
import com.unhappychoice.norimaki.scope.ViewScope
import dagger.Subcomponent
import mortar.MortarScope
import mortar.ViewPresenter
import javax.inject.Inject

class BuildListScreen : Screen() {
  override fun getLayoutResource() = R.layout.build_list_view
  override fun getSubComponent(activityComponent: ActivityComponent) = activityComponent.buildListScreenComponent()

  @Subcomponent @ViewScope interface Component {
    fun inject(view: BuildListView)
  }

  @ViewScope class Presenter @Inject constructor() : ViewPresenter<BuildListView>() {
    @Inject lateinit var activity: MainActivity
    private val token by lazy { APITokenPreference(activity).token }
    private val api by lazy { CircleCIAPIClient(token) }
    val builds = Variable<List<Build>>(listOf())

    override fun onEnterScope(scope: MortarScope?) {
      super.onEnterScope(scope)
      getBuilds()
    }

    override fun onExitScope() {
      super.onExitScope()
    }

    fun getBuilds() {
      api.client().getRecentBuilds().subscribeOnIoObserveOnUI().bindTo(builds)
    }

    fun goToBuildView(build: Build) {
      goTo(activity, BuildScreen(build))
    }
  }
}