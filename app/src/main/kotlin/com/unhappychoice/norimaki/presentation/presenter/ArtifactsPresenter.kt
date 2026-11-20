package com.unhappychoice.norimaki.presentation.presenter

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import com.github.unhappychoice.circleci.v1.response.Artifact
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.domain.model.BuildId
import com.unhappychoice.norimaki.domain.service.ArtifactDownloader
import com.unhappychoice.norimaki.domain.service.VerifiedSession
import com.unhappychoice.norimaki.extension.subscribeOnIoObserveOnUI
import com.unhappychoice.norimaki.presentation.presenter.core.PresenterNeedsToken
import com.unhappychoice.norimaki.presentation.view.ArtifactsView
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import org.kodein.di.instance
import java.io.IOException

class ArtifactsPresenter : PresenterNeedsToken<ArtifactsView>() {
    private val buildId: BuildId by instance()
    private val downloader = ArtifactDownloader()
    private var state: ArtifactListState = ArtifactListState.Loading
    private var downloading = false

    override fun onAuthenticated() = load()
    override fun onSessionEnded() {
        downloading = false
        view?.setDownloading(false)
        show(ArtifactListState.Failed)
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
        if (downloading) return
        activity.pickArtifactDestination(ArtifactSelection(buildId, artifact, verified))
    }

    internal fun save(selection: ArtifactSelection, destination: Uri) {
        val verified = account ?: return
        if (selection.session !== verified || selection.buildId != buildId || downloading) return
        val resolver = activity.contentResolver
        downloading = true
        view?.setDownloading(true)
        downloader.download(selection.artifact.url, verified.token, { deleteDocument(resolver, destination) }) {
            resolver.openOutputStream(destination, "w") ?: throw IOException("Could not open destination")
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeBy(
                onSuccess = { finishDownload(R.string.artifact_saved) },
                onError = { finishDownload(R.string.artifact_save_failed) }
            )
            .addTo(bag)
    }

    private fun deleteDocument(resolver: ContentResolver, destination: Uri) {
        runCatching { DocumentsContract.deleteDocument(resolver, destination) }
    }

    private fun finishDownload(message: Int) {
        downloading = false
        view?.setDownloading(false)
        view?.showMessage(message)
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

internal class ArtifactSelection(val buildId: BuildId, val artifact: Artifact, val session: VerifiedSession) {
    val filename: String get() = artifact.path.substringAfterLast('/').ifBlank { "artifact" }
}
