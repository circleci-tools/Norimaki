package com.unhappychoice.norimaki.screen.accessToken

import com.unhappychoice.norimaki.MainActivity
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.extension.Variable
import com.unhappychoice.norimaki.extension.goTo
import com.unhappychoice.norimaki.preference.APITokenPreference
import com.unhappychoice.norimaki.scope.ViewScope
import com.unhappychoice.norimaki.screen.Screen
import com.unhappychoice.norimaki.screen.builds.BuildsScreen
import mortar.MortarScope
import mortar.ViewPresenter
import javax.inject.Inject

class APITokenScreen : Screen() {
  override fun getLayoutResource() = R.layout.api_token_view

  @dagger.Component(dependencies = arrayOf(MainActivity.Component::class))
  @ViewScope
  interface Component {
    fun inject(view: APITokenView)
  }

  @ViewScope
  class Presenter @Inject constructor() : ViewPresenter<APITokenView>() {
    @Inject lateinit var activity: MainActivity
    val token: Variable<String> = Variable("")

    override fun onEnterScope(scope: MortarScope?) {
      super.onEnterScope(scope)
      token.value = APITokenPreference(activity).token
    }

    override fun onExitScope() {
      super.onExitScope()
    }

    fun saveToken() {
      APITokenPreference(activity).token = token.value
    }

    fun goToBuildList() = goTo(activity, BuildsScreen())
  }
}
