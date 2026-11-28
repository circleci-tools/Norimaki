package com.unhappychoice.norimaki.extension

import android.os.Looper
import hu.akarnokd.rxjava3.bridge.RxJavaBridge
import io.reactivex.Scheduler
import io.reactivex.rxjava3.android.plugins.RxAndroidPlugins
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers

internal fun androidMainScheduler(): Scheduler {
    // Let tests replace the scheduler before accessing the Android looper.
    if (RxAndroidPlugins.getOnMainThreadSchedulerHandler() != null) {
        return RxJavaBridge.toV2Scheduler(AndroidSchedulers.mainThread())
    }
    return mainScheduler
}

private val mainScheduler by lazy {
    RxJavaBridge.toV2Scheduler(AndroidSchedulers.from(Looper.getMainLooper(), false))
}
