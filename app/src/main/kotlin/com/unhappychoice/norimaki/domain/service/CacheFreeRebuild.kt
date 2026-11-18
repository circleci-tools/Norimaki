package com.unhappychoice.norimaki.domain.service

import com.github.unhappychoice.circleci.CircleCIAPIClientV1_1
import com.github.unhappychoice.circleci.v1.response.Build
import com.unhappychoice.norimaki.domain.model.BuildId
import io.reactivex.Observable
import io.reactivex.Scheduler
import io.reactivex.schedulers.Schedulers

internal fun CircleCIAPIClientV1_1.rebuildWithoutCache(
    buildId: BuildId,
    scheduler: Scheduler = Schedulers.io(),
    onFinished: () -> Unit = {}
): Observable<Build> {
    return Observable.defer {
        deleteCache(buildId.vcsType, buildId.owner, buildId.repository)
            .take(1)
            .flatMap { retryBuild(buildId.vcsType, buildId.owner, buildId.repository, buildId.number) }
    }
        .subscribeOn(scheduler)
        .doOnError { it.printStackTrace() }
        .doFinally { onFinished() }
        // Once started, the cached source stays subscribed even if the view stops observing.
        .cache()
}
