package com.unhappychoice.norimaki.extension

import hu.akarnokd.rxjava3.bridge.RxJavaBridge
import io.reactivex.Scheduler
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers

internal fun androidMainScheduler(): Scheduler =
    RxJavaBridge.toV2Scheduler(AndroidSchedulers.mainThread())
