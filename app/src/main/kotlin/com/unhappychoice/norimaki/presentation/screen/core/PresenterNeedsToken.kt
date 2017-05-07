package com.unhappychoice.norimaki.presentation.screen.core

import android.os.Handler
import android.view.View
import com.github.unhappychoice.circleci.CircleCIAPIClient
import com.github.unhappychoice.circleci.response.User
import com.unhappychoice.norimaki.MainActivity
import com.unhappychoice.norimaki.extension.goTo
import com.unhappychoice.norimaki.preference.APITokenPreference
import com.unhappychoice.norimaki.presentation.screen.APITokenScreen
import mortar.MortarScope
import mortar.ViewPresenter
import javax.inject.Inject

abstract class PresenterNeedsToken<T : View> : ViewPresenter<T>() {
  @Inject lateinit var activity: MainActivity
  val token by lazy { APITokenPreference(activity).token }
  val api by lazy { CircleCIAPIClient(token) }
  var currentUser: User? = null

  override fun onEnterScope(scope: MortarScope?) {
    super.onEnterScope(scope)
    if (token.isBlank()) {
      goToAPITokenView()
    }
  }

  private fun goToAPITokenView() {
    Handler().postDelayed({ goTo(activity, APITokenScreen()) }, 500)
  }
}

