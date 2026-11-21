package com.unhappychoice.norimaki.presentation.presenter.core

import android.os.Handler
import android.os.Looper
import android.view.View
import com.unhappychoice.norimaki.MainActivity
import com.unhappychoice.norimaki.domain.service.EventBusService
import com.unhappychoice.norimaki.domain.service.SessionService
import com.unhappychoice.norimaki.domain.service.VerifiedSession
import com.unhappychoice.norimaki.extension.subscribeNext
import com.unhappychoice.norimaki.presentation.screen.APITokenScreen
import flow.Direction
import flow.Flow
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposables
import io.reactivex.rxkotlin.addTo
import mortar.MortarScope
import org.kodein.di.instance

abstract class PresenterNeedsToken<T : View> : Presenter<T>() {
    val activity: MainActivity by instance()
    val eventBus: EventBusService by instance()
    private val session: SessionService by instance()
    private val lifecycle = CompositeDisposable()
    protected val bag = CompositeDisposable()
    @Volatile protected var account: VerifiedSession? = null
        private set

    override fun onEnterScope(scope: MortarScope?) {
        super.onEnterScope(scope)
        session.restore()
            .subscribe(::useAccount, { goToAPITokenView() })
            .addTo(lifecycle)
    }

    override fun dropView(view: T) {
        onViewDropped()
        super.dropView(view)
    }

    override fun onExitScope() {
        lifecycle.dispose()
        bag.dispose()
        super.onExitScope()
    }

    protected open fun onViewDropped() {
        account = null
        lifecycle.clear()
        bag.clear()
    }

    protected abstract fun onAuthenticated()
    protected open fun onSessionEnded() {}

    protected fun sessionEnded(verified: VerifiedSession) =
        session.changes.filter { it.toNullable() !== verified }.take(1)

    private fun useAccount(verified: VerifiedSession) {
        account = verified
        sessionEnded(verified)
            .subscribeNext {
                account = null
                bag.clear()
                onSessionEnded()
            }
            .addTo(lifecycle)
        onAuthenticated()
    }

    private fun goToAPITokenView() {
        val handler = Handler(Looper.getMainLooper())
        val redirect = Runnable { Flow.get(activity).replaceHistory(APITokenScreen(), Direction.REPLACE) }
        handler.postDelayed(redirect, 500)
        Disposables.fromAction { handler.removeCallbacks(redirect) }.addTo(lifecycle)
    }
}
