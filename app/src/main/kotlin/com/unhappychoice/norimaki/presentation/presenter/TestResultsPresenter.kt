package com.unhappychoice.norimaki.presentation.presenter

import android.os.Bundle
import com.unhappychoice.norimaki.domain.model.BuildId
import com.unhappychoice.norimaki.infrastructure.testresults.TestResults
import com.unhappychoice.norimaki.infrastructure.testresults.TestResultsClient
import com.unhappychoice.norimaki.infrastructure.testresults.TestResultsCursor
import com.unhappychoice.norimaki.presentation.presenter.core.PresenterNeedsToken
import com.unhappychoice.norimaki.presentation.view.TestResultsView
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import org.kodein.di.factory
import org.kodein.di.instance

class TestResultsPresenter : PresenterNeedsToken<TestResultsView>() {
    private val buildId: BuildId by instance()
    private val createClient: (String) -> TestResultsClient by factory()
    private var needsSessionRestore = false
    private var nextCursor: TestResultsCursor? = null

    override fun onAuthenticated() = load()

    override fun onLoad(savedInstanceState: Bundle?) {
        super.onLoad(savedInstanceState)
        if (!needsSessionRestore) return
        needsSessionRestore = false
        restoreSession()
    }

    override fun onViewDropped() {
        needsSessionRestore = true
        nextCursor = null
        super.onViewDropped()
    }

    internal fun load() {
        val verified = account ?: return
        val view = view ?: return
        bag.clear()
        view.showLoading()
        createClient(verified.token).fetch(buildId, nextCursor)
            .observeOn(AndroidSchedulers.mainThread(), true)
            .takeUntil(sessionEnded(verified))
            .subscribeBy(
                onNext = { results ->
                    when (results) {
                        TestResults.Unavailable -> view.showUnavailable()
                        TestResults.Reported -> view.finishLoading()
                        is TestResults.FailurePage -> {
                            view.appendFailures(results.failures)
                            nextCursor = results.nextCursor
                        }
                    }
                },
                onError = { view.showLoadError() }
            )
            .addTo(bag)
    }
}
