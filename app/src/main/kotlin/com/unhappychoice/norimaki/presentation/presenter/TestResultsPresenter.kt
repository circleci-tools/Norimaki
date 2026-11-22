package com.unhappychoice.norimaki.presentation.presenter

import com.unhappychoice.norimaki.domain.model.BuildId
import com.unhappychoice.norimaki.domain.service.SessionService
import com.unhappychoice.norimaki.infrastructure.testresults.TestResults
import com.unhappychoice.norimaki.infrastructure.testresults.TestResultsClient
import com.unhappychoice.norimaki.presentation.presenter.core.Presenter
import com.unhappychoice.norimaki.presentation.view.TestResultsView
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.subscribeBy
import org.kodein.di.factory
import org.kodein.di.instance

class TestResultsPresenter : Presenter<TestResultsView>() {
    private val buildId: BuildId by instance()
    private val session: SessionService by instance()
    private val createClient: (String) -> TestResultsClient by factory()
    private var request: Disposable? = null

    internal fun load(view: TestResultsView) {
        request?.dispose()
        request = session.restore().flatMapObservable { verified ->
            createClient(verified.token).fetch(buildId)
                .takeUntil(session.changes.filter { it.toNullable() !== verified })
        }
            .observeOn(AndroidSchedulers.mainThread(), true)
            .subscribeBy(
                onNext = { results ->
                    when (results) {
                        TestResults.Unavailable -> view.showUnavailable()
                        TestResults.Reported -> view.finishLoading()
                        is TestResults.FailurePage -> view.appendFailures(results.failures)
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
