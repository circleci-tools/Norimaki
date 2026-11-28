package com.unhappychoice.norimaki.domain.service

import com.github.unhappychoice.circleci.CircleCIAPIClientV1_1
import com.github.unhappychoice.circleci.v1.response.Build
import com.unhappychoice.norimaki.domain.model.BuildId
import io.reactivex.Observable

internal fun CircleCIAPIClientV1_1.rebuildWithoutCache(buildId: BuildId): Observable<Build> =
    deleteCache(buildId.vcsType, buildId.owner, buildId.repository)
        .take(1)
        .flatMap { retryBuild(buildId.vcsType, buildId.owner, buildId.repository, buildId.number) }
