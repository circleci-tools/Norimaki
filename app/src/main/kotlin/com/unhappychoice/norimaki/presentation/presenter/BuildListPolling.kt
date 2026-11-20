package com.unhappychoice.norimaki.presentation.presenter

import com.github.unhappychoice.circleci.v1.response.Build
import com.unhappychoice.norimaki.domain.model.uniqueId
import io.reactivex.Observable

internal const val BUILD_LIST_PAGE_SIZE = 20

internal fun fetchLatestBuilds(
    knownIds: Set<String>,
    fetchPage: (Int) -> Observable<List<Build>>
): Observable<List<Build>> = Observable.defer {
    var offset = 0
    var shouldStop = false

    Observable.defer {
        shouldStop = true
        fetchPage(offset).doOnNext { page ->
            shouldStop = page.size < BUILD_LIST_PAGE_SIZE || page.any { it.uniqueId() in knownIds }
            offset += BUILD_LIST_PAGE_SIZE
        }
    }.repeatUntil { shouldStop }
        .toList()
        .map { it.flatten() }
        .toObservable()
}
