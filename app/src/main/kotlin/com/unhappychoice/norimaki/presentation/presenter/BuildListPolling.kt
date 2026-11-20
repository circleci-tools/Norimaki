package com.unhappychoice.norimaki.presentation.presenter

import com.github.unhappychoice.circleci.v1.response.Build
import com.unhappychoice.norimaki.domain.model.uniqueId
import io.reactivex.Observable

internal const val BUILD_LIST_PAGE_SIZE = 20

internal class BuildListPolling(private val fetchPage: (Int) -> Observable<List<Build>>) {
    private var backfillBoundary: Set<String>? = null

    fun fetchLatestBuilds(knownIds: Set<String>): Observable<List<Build>> = Observable.defer {
        val boundary = backfillBoundary ?: knownIds
        var offset = 0
        var shouldStop = false

        Observable.defer {
            shouldStop = true
            fetchPage(offset).doOnNext { page ->
                shouldStop = page.size < BUILD_LIST_PAGE_SIZE || page.any { it.uniqueId() in boundary }
                // Retry from the head using the original boundary, since newer builds can shift offsets.
                if (!shouldStop) backfillBoundary = boundary
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
