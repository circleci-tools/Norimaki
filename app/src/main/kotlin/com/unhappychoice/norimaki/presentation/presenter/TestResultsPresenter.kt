package com.unhappychoice.norimaki.presentation.presenter

import android.os.Bundle
import com.github.unhappychoice.circleci.v1.response.Build
import com.unhappychoice.norimaki.infrastructure.testresults.TestResults
import com.unhappychoice.norimaki.infrastructure.testresults.TestResultsClient
import com.unhappychoice.norimaki.presentation.presenter.core.PresenterNeedsToken
import com.unhappychoice.norimaki.presentation.view.TestResultsView
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import org.kodein.di.factory
import org.kodein.di.instance

class TestResultsPresenter : PresenterNeedsToken<TestResultsView>() {
    private val build: Build by instance()
    private val createClient: (String) -> TestResultsClient by factory()
    private var needsSessionRestore = false

    override fun onAuthenticated() = load()

    override fun onLoad(savedInstanceState: Bundle?) {
        super.onLoad(savedInstanceState)
        if (!needsSessionRestore) return
        needsSessionRestore = false
        restoreSession()
    }

    override fun onViewDropped() {
        needsSessionRestore = true
        super.onViewDropped()
    }

    internal fun load() {
        val verified = account ?: return
        val view = view ?: return
        bag.clear()
        view.showLoading()
        createClient(verified.token).fetch(build)
            .toObservable()
            .observeOn(AndroidSchedulers.mainThread())
            .takeUntil(sessionEnded(verified))
            .subscribeBy(
                onNext = { results ->
                    when (results) {
                        TestResults.Unavailable -> view.showUnavailable()
                        is TestResults.Reported -> view.showFailures(results.failures)
                    }
                },
                onError = { view.showLoadError() }
            )
            .addTo(bag)
    }
}
