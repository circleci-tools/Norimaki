package com.unhappychoice.norimaki.presentation.presenter

import com.unhappychoice.norimaki.domain.model.BuildId
import com.unhappychoice.norimaki.domain.service.SessionService
import com.unhappychoice.norimaki.domain.service.VerifiedSession
import com.unhappychoice.norimaki.infrastructure.testresults.TestResults
import com.unhappychoice.norimaki.infrastructure.testresults.TestResultsClient
import com.unhappychoice.norimaki.infrastructure.testresults.TestResultsCursor
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
    private var loadedSession: VerifiedSession? = null
    private var nextCursor: TestResultsCursor? = null

    internal fun load(view: TestResultsView) {
        request?.dispose()
        request = session.restore()
            .observeOn(AndroidSchedulers.mainThread())
            .flatMapObservable { verified ->
                if (loadedSession !== verified) {
                    loadedSession = verified
                    nextCursor = null
                    view.clearFailures()
                }
                createClient(verified.token).fetch(buildId, nextCursor)
                    .takeUntil(session.changes.filter { it.toNullable() !== verified })
            }
            .observeOn(AndroidSchedulers.mainThread(), true)
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
    }

    override fun dropView(view: TestResultsView) {
        request?.dispose()
        request = null
        loadedSession = null
        nextCursor = null
        super.dropView(view)
    }
}
