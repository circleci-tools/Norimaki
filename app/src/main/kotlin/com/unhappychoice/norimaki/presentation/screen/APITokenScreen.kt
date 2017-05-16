package com.unhappychoice.norimaki.presentation.screen

import com.unhappychoice.norimaki.ActivityComponent
import com.unhappychoice.norimaki.MainActivity
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.extension.Variable
import com.unhappychoice.norimaki.extension.goTo
import com.unhappychoice.norimaki.preference.APITokenPreference
import com.unhappychoice.norimaki.presentation.screen.core.Screen
import com.unhappychoice.norimaki.presentation.view.APITokenView
import com.unhappychoice.norimaki.scope.ViewScope
import dagger.Subcomponent
import mortar.MortarScope
import mortar.ViewPresenter
import javax.inject.Inject

class APITokenScreen : Screen() {
  override fun getLayoutResource() = R.layout.api_token_view
  override fun getSubComponent(activityComponent: ActivityComponent) = activityComponent.apiTokenScreenComponent()
  override fun getTitle() = "Set api token"

  @Subcomponent @ViewScope interface Component {
    fun inject(view: APITokenView)
  }

  @ViewScope class Presenter @Inject constructor() : ViewPresenter<APITokenView>() {
    @Inject lateinit var activity: MainActivity
    val token: Variable<String> = Variable("")

    override fun onEnterScope(scope: MortarScope?) {
      super.onEnterScope(scope)
      token.value = APITokenPreference(activity.applicationContext).token
    }

    fun saveToken() {
      APITokenPreference(activity).token = token.value
    }

    fun goToBuildList() = goTo(activity, BuildListScreen())
  }
}
