package com.unhappychoice.norimaki.domain.service

import com.github.unhappychoice.circleci.CircleCIAPIClientV1_1
import com.github.unhappychoice.circleci.v1.response.Build
import com.unhappychoice.norimaki.domain.model.vcsType
import io.reactivex.Observable
import io.reactivex.Scheduler
import io.reactivex.schedulers.Schedulers

internal fun CircleCIAPIClientV1_1.rebuildWithoutCache(
    build: Build,
    scheduler: Scheduler = Schedulers.io()
): Observable<Build> {
    val vcs = build.vcsType()
    val owner = build.username!!
    val repo = build.reponame!!
    val number = build.buildNum!!
    return Observable.defer { deleteCache(vcs, owner, repo) }
        .take(1)
        .flatMap { retryBuild(vcs, owner, repo, number) }
        .subscribeOn(scheduler)
        .doOnError { it.printStackTrace() }
        // Once started, the cached source stays subscribed even if the view stops observing.
        .cache()
}
