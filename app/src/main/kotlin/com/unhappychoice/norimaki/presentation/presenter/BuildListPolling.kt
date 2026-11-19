package com.unhappychoice.norimaki.presentation.presenter

import com.github.unhappychoice.circleci.v1.response.Build
import com.unhappychoice.norimaki.domain.model.uniqueId
import io.reactivex.Observable

internal const val BUILD_LIST_PAGE_SIZE = 20

internal class BuildListPolling(private val fetchPage: (Int) -> Observable<List<Build>>) {
    private var backfillBoundary: Set<String>? = null

    fun fetchLatestBuilds(knownBuilds: List<Build>): Observable<List<Build>> = Observable.defer {
        val knownIds = backfillBoundary ?: knownBuilds.mapTo(hashSetOf()) { it.uniqueId() }
        var offset = 0
        var shouldStop = false

        Observable.defer {
            shouldStop = true
            fetchPage(offset).doOnNext { page ->
                shouldStop = page.size < BUILD_LIST_PAGE_SIZE || page.any { it.uniqueId() in knownIds }
                // Retry from the head using the original boundary, since newer builds can shift offsets.
                if (!shouldStop) backfillBoundary = knownIds
                offset += BUILD_LIST_PAGE_SIZE
            }
        }.repeatUntil { shouldStop }
            .doOnComplete { backfillBoundary = null }
            .onErrorResumeNext(Observable.empty<List<Build>>())
            .toList()
            .filter { it.isNotEmpty() }
            .map { it.flatten() }
            .toObservable()
    }
}
