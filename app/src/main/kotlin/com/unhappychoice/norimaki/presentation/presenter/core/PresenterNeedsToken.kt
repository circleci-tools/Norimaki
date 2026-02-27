package com.unhappychoice.norimaki.presentation.presenter.core

import android.os.Handler
import android.view.View
import com.github.unhappychoice.circleci.CircleCIAPIClientV2
import com.unhappychoice.norimaki.MainActivity
import com.unhappychoice.norimaki.domain.service.EventBusService
import com.unhappychoice.norimaki.extension.goTo
import com.unhappychoice.norimaki.extension.subscribeNext
import com.unhappychoice.norimaki.extension.subscribeOnIoObserveOnUI
import com.unhappychoice.norimaki.extension.withLog
import com.unhappychoice.norimaki.infrastructure.preference.APITokenPreference
import com.unhappychoice.norimaki.presentation.screen.APITokenScreen
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import mortar.MortarScope
import org.kodein.di.instance

abstract class PresenterNeedsToken<T : View> : Presenter<T>() {
    val activity: MainActivity by instance()
    val api: CircleCIAPIClientV2 by instance()
    val eventBus: EventBusService by instance()

    val token
        get() = APITokenPreference(activity).token

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

        api.getMe()
            .subscribeOnIoObserveOnUI()
            .withLog("getMe")
            .doOnError { goToAPITokenView() }
            .subscribeNext { eventBus.authenticated.onNext(token) }
            .addTo(bag)
    }

    private fun goToAPITokenView() {
        Handler().postDelayed({ goTo(activity, APITokenScreen()) }, 500)
    }
}
