package com.unhappychoice.norimaki.presentation.presenter

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.github.unhappychoice.circleci.v1.response.Artifact
import com.unhappychoice.norimaki.ArtifactDownloadState
import com.unhappychoice.norimaki.domain.model.BuildId
import com.unhappychoice.norimaki.extension.subscribeNext
import com.unhappychoice.norimaki.extension.subscribeOnIoObserveOnUI
import com.unhappychoice.norimaki.presentation.presenter.core.PresenterNeedsToken
import com.unhappychoice.norimaki.presentation.view.ArtifactsView
import io.reactivex.Observable
import io.reactivex.disposables.Disposables
import io.reactivex.disposables.SerialDisposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import org.kodein.di.instance

class ArtifactsPresenter : PresenterNeedsToken<ArtifactsView>() {
    private val buildId: BuildId by instance()
    private var state: ArtifactListState = ArtifactListState.Loading
    override fun onAuthenticated() = load()

    override fun onSessionEnded() {
        view?.setDownloading(false)
        show(ArtifactListState.Failed)
    }

    fun observeDownloads() {
        val lifecycle = activity.lifecycle
        val subscription = SerialDisposable().apply { addTo(bag) }
        val observer = object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                subscription.set(activity.artifactDownloads.changes.subscribeNext(::renderDownload))
            }

            override fun onPause(owner: LifecycleOwner) {
                subscription.set(null)
            }
        }
        lifecycle.addObserver(observer)
        Disposables.fromAction { lifecycle.removeObserver(observer) }.addTo(bag)
    }

    fun render() {
        view?.render(state)
    }

    fun load() {
        val client = account?.client ?: return
        show(ArtifactListState.Loading)
        Observable.defer { client.getArtifacts(buildId.vcsType, buildId.owner, buildId.repository, buildId.number) }
            .subscribeOnIoObserveOnUI()
            .subscribeBy(
                onNext = { show(ArtifactListState.Loaded(it)) },
                onError = { show(ArtifactListState.Failed) }
            )
            .addTo(bag)
    }

    fun chooseDestination(artifact: Artifact) {
        val verified = account ?: return
        activity.pickArtifactDestination(ArtifactSelection(buildId, artifact, verified))
    }

    private fun renderDownload(state: ArtifactDownloadState.Transfer) {
        val artifacts = view ?: return
        artifacts.setDownloading(state == ArtifactDownloadState.Transfer.Running(buildId))
        if (state is ArtifactDownloadState.Transfer.Finished && state.buildId == buildId &&
            activity.artifactDownloads.consume(state)
        ) {
            artifacts.showMessage(state.message)
        }
    }

    private fun show(next: ArtifactListState) {
        state = next
        render()
    }
}

internal sealed interface ArtifactListState {
    data object Loading : ArtifactListState
    data object Failed : ArtifactListState
    data class Loaded(val artifacts: List<Artifact>) : ArtifactListState
}
