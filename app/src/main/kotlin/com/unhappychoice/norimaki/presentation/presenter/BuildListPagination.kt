package com.unhappychoice.norimaki.presentation.presenter

import com.github.unhappychoice.circleci.v1.response.Build
import com.unhappychoice.norimaki.domain.model.uniqueId
import io.reactivex.Observable

internal data class OlderBuildPage(val offset: Int, val builds: List<Build>)

internal fun fetchOlderBuilds(
    fetch: (Int) -> Observable<List<Build>>,
    tail: Build,
    retainedIds: Set<String>,
    startOffset: Int
): Observable<OlderBuildPage> = Observable.defer {
    val tailId = tail.uniqueId()
    var offset = startOffset
    var canRewind = startOffset > 0

    Observable.defer {
        val pageOffset = offset
        fetch(pageOffset).singleOrError().toObservable().map { page ->
            val anchor = page.indexOfFirst { it.uniqueId() == tailId }
            val exhausted = page.size < BUILD_LIST_PAGE_SIZE
            val rewind = canRewind && anchor < 0 &&
                (exhausted || page.any { compareValues(it.queuedAt, tail.queuedAt) <= 0 })
            canRewind = false
            offset = if (rewind) 0 else pageOffset + BUILD_LIST_PAGE_SIZE
            val older = when {
                rewind -> emptyList()
                anchor >= 0 -> page.drop(anchor + 1)
                else -> page.filter { compareValues(it.queuedAt, tail.queuedAt) <= 0 }
            }.filter { it.uniqueId() !in retainedIds }
            OlderBuildPage(pageOffset, older) to (exhausted && !rewind)
        }
    }.repeat()
        .filter { (page, exhausted) -> page.builds.isNotEmpty() || exhausted }
        .take(1)
        .map { (page, _) -> page }
}
