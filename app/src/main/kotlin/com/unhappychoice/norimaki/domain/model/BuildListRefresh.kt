package com.unhappychoice.norimaki.domain.model

import com.github.unhappychoice.circleci.v1.response.Build
import io.reactivex.Observable

internal fun refreshBuildList(
    displayed: List<Build>,
    latest: List<Build>,
    fetchDetails: (Build) -> Observable<Build>
): Observable<List<Build>> {
    val latestIds = latest.map { it.uniqueId() }.toSet()
    val missingUnfinished = displayed.filter { !it.isFinished() && it.uniqueId() !in latestIds }
    return Observable.fromIterable(missingUnfinished)
        .concatMap { build ->
            Observable.defer { fetchDetails(build) }.onErrorResumeNext(Observable.empty<Build>())
        }
        .toList()
        .filter { it.isNotEmpty() }
        .toObservable()
        .startWith(latest)
}
