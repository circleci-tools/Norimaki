package com.unhappychoice.norimaki.presentation.presenter

import com.github.unhappychoice.circleci.v1.response.Build
import com.unhappychoice.norimaki.domain.model.uniqueId
import io.reactivex.Observable

internal const val BUILD_LIST_PAGE_SIZE = 20
internal const val BUILD_POLL_PAGE_LIMIT = BUILD_HISTORY_LIMIT / BUILD_LIST_PAGE_SIZE

internal class BuildListPolling(private val fetchPage: (Int) -> Observable<List<Build>>) {
    private var backfillBoundary: Set<String>? = null

    fun fetchLatestBuilds(knownIds: Set<String>): Observable<List<Build>> = Observable.defer {
        val boundary = backfillBoundary ?: knownIds
        var pagesFetched = 0
        var shouldStop = false

        Observable.defer {
            shouldStop = true
            fetchPage(pagesFetched * BUILD_LIST_PAGE_SIZE)
                .take(1)
                .map { it.take(BUILD_LIST_PAGE_SIZE) }
                .doOnNext { page ->
                    pagesFetched++
                    shouldStop = pagesFetched == BUILD_POLL_PAGE_LIMIT ||
                        page.size < BUILD_LIST_PAGE_SIZE || page.any { it.uniqueId() in boundary }
                    // Retry failed requests from the head, since newer builds can shift offsets.
                    if (!shouldStop) backfillBoundary = boundary
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
