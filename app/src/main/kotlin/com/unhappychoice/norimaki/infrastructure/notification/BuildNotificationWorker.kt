package com.unhappychoice.norimaki.infrastructure.notification

import android.content.Context
import androidx.work.RxWorker
import androidx.work.WorkerParameters
import com.github.unhappychoice.circleci.CircleCIAPIClient
import com.github.unhappychoice.circleci.v1.response.Build
import com.unhappychoice.norimaki.domain.model.finishedAfter
import com.unhappychoice.norimaki.domain.model.latestStopTime
import com.unhappychoice.norimaki.infrastructure.preference.APITokenPreference
import com.unhappychoice.norimaki.infrastructure.preference.NotificationPreference
import io.reactivex.Single
import java.util.Date

class BuildNotificationWorker(context: Context, params: WorkerParameters) : RxWorker(context, params) {
    private val token by lazy { APITokenPreference(applicationContext).token }
    private val preference by lazy { NotificationPreference(applicationContext) }
    private val notifier by lazy { BuildNotifier(applicationContext) }

    override fun createWork(): Single<Result> =
        if (token.isBlank()) Single.just(Result.success()) else checkBuilds()

    private fun checkBuilds(): Single<Result> =
        CircleCIAPIClient(token).client1_1()
            .getRecentBuilds(offset = 0, limit = RECENT_BUILDS_LIMIT)
            .firstOrError()
            .doOnSuccess { notifyFinishedBuilds(it) }
            .map { Result.success() }
            .onErrorReturnItem(Result.retry())

    private fun notifyFinishedBuilds(builds: List<Build>) {
        val lastStopTime = preference.lastStopTime
        if (lastStopTime > 0) builds.finishedAfter(Date(lastStopTime)).forEach { notifier.notify(it) }
        builds.latestStopTime()?.let { preference.lastStopTime = maxOf(lastStopTime, it.time) }
    }

    companion object {
        private const val RECENT_BUILDS_LIMIT = 30
    }
}
