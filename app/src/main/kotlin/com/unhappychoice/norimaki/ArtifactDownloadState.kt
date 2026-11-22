package com.unhappychoice.norimaki

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.ViewModel
import com.unhappychoice.norimaki.domain.model.BuildId
import com.unhappychoice.norimaki.domain.service.ArtifactDownloader
import com.unhappychoice.norimaki.domain.service.SessionService
import com.unhappychoice.norimaki.presentation.presenter.ArtifactSelection
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.BehaviorSubject
import java.io.IOException

internal class ArtifactDownloadState : ViewModel() {
    sealed interface Transfer {
        data object Idle : Transfer
        data class Running(val buildId: BuildId) : Transfer
        class Finished(val buildId: BuildId, val message: Int) : Transfer
    }

    var selection: ArtifactSelection? = null
    var destination: Pair<ArtifactSelection, Uri>? = null
    private val transfer = BehaviorSubject.createDefault<Transfer>(Transfer.Idle)
    private var reportedResult: Transfer.Finished? = null
    private val operation = CompositeDisposable()
    val changes: Observable<Transfer> = transfer.hide()
    val isBusy: Boolean get() = selection != null || destination != null || isDownloading
    private val isDownloading: Boolean get() = transfer.value is Transfer.Running

    fun save(
        selection: ArtifactSelection,
        destination: Uri,
        resolver: ContentResolver,
        session: SessionService,
        downloader: ArtifactDownloader
    ) {
        if (isDownloading) return
        operation.clear()
        reportedResult = null
        transfer.onNext(Transfer.Running(selection.buildId))
        session.restore()
            .flatMapObservable { verified ->
                if (!selection.matches(verified)) return@flatMapObservable Observable.empty<Transfer>()
                val sessionEnded = session.changes.filter { it.toNullable() !== verified }
                download(selection, destination, resolver, downloader, verified.token)
                    .concatWith(Observable.never<Transfer>())
                    .takeUntil(sessionEnded)
            }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeBy(
                onNext = transfer::onNext,
                onComplete = { transfer.onNext(Transfer.Idle) },
                onError = { transfer.onNext(Transfer.Idle) }
            )
            .addTo(operation)
    }

    fun consume(result: Transfer.Finished): Boolean {
        if (transfer.value !== result || reportedResult === result) return false
        reportedResult = result
        operation.clear()
        return true
    }

    override fun onCleared() {
        operation.dispose()
    }

    private fun download(
        selection: ArtifactSelection,
        destination: Uri,
        resolver: ContentResolver,
        downloader: ArtifactDownloader,
        token: String
    ): Observable<Transfer> =
        downloader.download(selection.url, token, {
            runCatching { DocumentsContract.deleteDocument(resolver, destination) }
        }) {
            resolver.openOutputStream(destination, "w") ?: throw IOException("Could not open destination")
        }
            .subscribeOn(Schedulers.io())
            .map<Transfer> { Transfer.Finished(selection.buildId, R.string.artifact_saved) }
            .onErrorReturn { Transfer.Finished(selection.buildId, R.string.artifact_save_failed) }
            .toObservable()
}
