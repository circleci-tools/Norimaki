package com.unhappychoice.norimaki.presentation.presenter.core

import com.unhappychoice.norimaki.extension.goTo
import com.unhappychoice.norimaki.extension.subscribeNext
import com.unhappychoice.norimaki.extension.subscribeOnIoObserveOnUI
import com.unhappychoice.norimaki.extension.withLog
import io.reactivex.rxkotlin.addTo

abstract class PresenterNeedsToken<T : android.view.View>(
    open val activity: com.unhappychoice.norimaki.MainActivity,
    open val api: com.github.unhappychoice.circleci.CircleCIAPIClientV1,
    open val eventBus: com.unhappychoice.norimaki.domain.service.EventBusService,
    open val pusher: com.unhappychoice.norimaki.infrastructure.pusher.PusherService
) : mortar.ViewPresenter<T>() {
    companion object {
        var currentUser: com.github.unhappychoice.circleci.response.User? = null
    }

    val token by lazy { com.unhappychoice.norimaki.infrastructure.preference.APITokenPreference(activity).token }
    val bag = io.reactivex.disposables.CompositeDisposable()

    override fun onEnterScope(scope: mortar.MortarScope?) {
        super.onEnterScope(scope)
        authenticate()
    }

    override fun onExitScope() {
        bag.dispose()
        super.onExitScope()
    }

    private fun authenticate() {
        if (token.isBlank()) return goToAPITokenView()
        if (com.unhappychoice.norimaki.presentation.presenter.core.PresenterNeedsToken.Companion.currentUser != null) return

        api.getMe()
            .subscribeOnIoObserveOnUI()
            .withLog("getMe")
            .doOnError { goToAPITokenView() }
            .doOnNext { com.unhappychoice.norimaki.presentation.presenter.core.PresenterNeedsToken.Companion.currentUser = it }
            .subscribeNext { eventBus.authenticated.onNext(Pair(token, it.pusherId)) }
            .addTo(bag)
    }

    private fun goToAPITokenView() {
        android.os.Handler().postDelayed({ goTo(activity, com.unhappychoice.norimaki.presentation.screen.APITokenScreen()) }, 500)
    }
}

