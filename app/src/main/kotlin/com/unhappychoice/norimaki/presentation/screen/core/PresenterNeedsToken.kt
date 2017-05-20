package com.unhappychoice.norimaki.presentation.screen.core

import android.os.Handler
import android.view.View
import com.github.unhappychoice.circleci.CircleCIAPIClientV1
import com.github.unhappychoice.circleci.response.User
import com.unhappychoice.norimaki.MainActivity
import com.unhappychoice.norimaki.domain.service.EventBusService
import com.unhappychoice.norimaki.extension.goTo
import com.unhappychoice.norimaki.extension.subscribeNext
import com.unhappychoice.norimaki.extension.subscribeOnIoObserveOnUI
import com.unhappychoice.norimaki.extension.withLog
import com.unhappychoice.norimaki.infrastructure.pusher.PusherService
import com.unhappychoice.norimaki.preference.APITokenPreference
import com.unhappychoice.norimaki.presentation.screen.APITokenScreen
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import mortar.MortarScope
import mortar.ViewPresenter
import javax.inject.Inject

abstract class PresenterNeedsToken<T : View> : ViewPresenter<T>() {
  @Inject lateinit var activity: MainActivity
  @Inject lateinit var api: CircleCIAPIClientV1
  @Inject lateinit var eventBus: EventBusService
  @Inject lateinit var pusher: PusherService

  companion object {
    var currentUser: User? = null
  }

  val token by lazy { APITokenPreference(activity).token }
  val bag = CompositeDisposable()

  override fun onEnterScope(scope: MortarScope?) {
    super.onEnterScope(scope)
    authenticate()
  }

  override fun onExitScope() {
    bag.dispose()
    super.onExitScope()
  }

  private fun authenticate() {
    if (token.isBlank()) return goToAPITokenView()
    if (currentUser != null) return

    api.getMe()
      .subscribeOnIoObserveOnUI()
      .withLog("getMe")
      .doOnError { goToAPITokenView() }
      .doOnNext { currentUser = it }
      .subscribeNext { eventBus.authenticated.onNext(Pair(token, it.pusherId)) }
      .addTo(bag)
  }

  private fun goToAPITokenView() {
    Handler().postDelayed({ goTo(activity, APITokenScreen()) }, 500)
  }
}

