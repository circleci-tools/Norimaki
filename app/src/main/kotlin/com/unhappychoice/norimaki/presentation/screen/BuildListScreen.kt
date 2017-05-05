package com.unhappychoice.norimaki.presentation.screen

import com.github.unhappychoice.circleci.response.Build
import com.unhappychoice.norimaki.ActivityComponent
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.extension.Variable
import com.unhappychoice.norimaki.extension.bindTo
import com.unhappychoice.norimaki.extension.goTo
import com.unhappychoice.norimaki.extension.subscribeOnIoObserveOnUI
import com.unhappychoice.norimaki.presentation.screen.core.PresenterNeedsToken
import com.unhappychoice.norimaki.presentation.screen.core.Screen
import com.unhappychoice.norimaki.presentation.view.BuildListView
import com.unhappychoice.norimaki.scope.ViewScope
import dagger.Subcomponent
import mortar.MortarScope
import javax.inject.Inject

class BuildListScreen : Screen() {
  override fun getLayoutResource() = R.layout.build_list_view
  override fun getSubComponent(activityComponent: ActivityComponent) = activityComponent.buildListScreenComponent()

  @Subcomponent @ViewScope interface Component {
    fun inject(view: BuildListView)
  }

  @ViewScope class Presenter @Inject constructor() : PresenterNeedsToken<BuildListView>() {
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