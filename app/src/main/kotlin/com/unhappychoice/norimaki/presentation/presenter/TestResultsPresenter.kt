package com.unhappychoice.norimaki.presentation.presenter

import com.github.unhappychoice.circleci.v1.response.Build
import com.unhappychoice.norimaki.infrastructure.testresults.TestResults
import com.unhappychoice.norimaki.infrastructure.testresults.TestResultsClient
import com.unhappychoice.norimaki.presentation.presenter.core.Presenter
import com.unhappychoice.norimaki.presentation.view.TestResultsView
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.subscribeBy
import org.kodein.di.instance

class TestResultsPresenter : Presenter<TestResultsView>() {
    private val build: Build by instance()
    private val client: TestResultsClient by instance()
    private var request: Disposable? = null

    internal fun load(view: TestResultsView) {
        request?.dispose()
        request = client.fetch(build)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeBy(
                onSuccess = { results ->
                    when (results) {
                        TestResults.Unavailable -> view.showUnavailable()
                        is TestResults.Reported -> view.showFailures(results.failures)
                    }
                },
                onError = { view.showLoadError() }
            )
    }

    override fun dropView(view: TestResultsView) {
        request?.dispose()
        request = null
        super.dropView(view)
    }
}
