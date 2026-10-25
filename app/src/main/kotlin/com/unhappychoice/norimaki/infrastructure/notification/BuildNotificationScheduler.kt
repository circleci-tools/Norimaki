package com.unhappychoice.norimaki.infrastructure.notification

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object BuildNotificationScheduler {
    private const val WORK_NAME = "build_notification"
    private const val INTERVAL_MINUTES = 15L

    fun schedule(context: Context) {
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request())
    }

    private fun request() =
        PeriodicWorkRequestBuilder<BuildNotificationWorker>(INTERVAL_MINUTES, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
}
