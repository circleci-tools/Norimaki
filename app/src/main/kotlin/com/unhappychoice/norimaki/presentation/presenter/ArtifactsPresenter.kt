package com.unhappychoice.norimaki.presentation.presenter

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import com.github.unhappychoice.circleci.v1.response.Artifact
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.domain.model.BuildId
import com.unhappychoice.norimaki.domain.service.ArtifactDownloader
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
    private val downloader: ArtifactDownloader by instance()
    private var state: ArtifactListState = ArtifactListState.Loading
    private var downloading = false
    private var pendingSave: Pair<ArtifactSelection, Uri>? = null

    override fun onAuthenticated() {
        val result = pendingSave
        pendingSave = null
        result?.let { (selection, destination) -> save(selection, destination) }
        load()
    }

    override fun onSessionEnded() {
        pendingSave = null
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
        if (selection.buildId != buildId || downloading) return
        val verified = account
        if (verified == null) {
            if (selection.isRestored) pendingSave = selection to destination
            return
        }
        if (!selection.matches(verified)) return
        val resolver = activity.contentResolver
        downloading = true
        view?.setDownloading(true)
        downloader.download(selection.url, verified.token, { deleteDocument(resolver, destination) }) {
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
