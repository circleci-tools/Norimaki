package com.unhappychoice.norimaki.presentation.presenter

import com.unhappychoice.norimaki.domain.model.ProjectId
import com.unhappychoice.norimaki.extension.androidMainScheduler
import com.unhappychoice.norimaki.infrastructure.flakytests.FlakyTestSummary
import com.unhappychoice.norimaki.infrastructure.flakytests.FlakyTestsClient
import com.unhappychoice.norimaki.presentation.presenter.core.PresenterNeedsToken
import com.unhappychoice.norimaki.presentation.view.FlakyTestsView
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import org.kodein.di.factory
import org.kodein.di.instance

class FlakyTestsPresenter : PresenterNeedsToken<FlakyTestsView>() {
    private val projectId: ProjectId by instance()
    private val createClient: (String) -> FlakyTestsClient by factory()
    private var state: FlakyTestsState = FlakyTestsState.Loading

    override fun onAuthenticated() = load()

    override fun onSessionEnded() {
        show(FlakyTestsState.Failed)
    }

    fun render() {
        view?.render(state)
    }

    fun load() {
        val verified = account ?: return
        bag.clear()
        show(FlakyTestsState.Loading)
        createClient(verified.token).fetch(projectId)
            .observeOn(androidMainScheduler())
            .takeUntil(sessionEnded(verified))
            .subscribeBy(
                onNext = { show(FlakyTestsState.Loaded(it)) },
                onError = { show(FlakyTestsState.Failed) }
            )
            .addTo(bag)
    }

    private fun show(next: FlakyTestsState) {
        state = next
        render()
    }
}

internal sealed interface FlakyTestsState {
    data object Loading : FlakyTestsState
    data object Failed : FlakyTestsState
    data class Loaded(val tests: List<FlakyTestSummary>) : FlakyTestsState
}
