package com.unhappychoice.norimaki.extension

import io.reactivex.Observable
import io.reactivex.Scheduler
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.TimeUnit

fun <T : Any> polling(
    period: Long,
    unit: TimeUnit = TimeUnit.SECONDS,
    scheduler: Scheduler = Schedulers.computation(),
    fetch: () -> Observable<T>
): Observable<T> =
    Observable.defer(fetch)
        .onErrorResumeNext(Observable.empty<T>())
        .repeatWhen { completions -> completions.delay(period, unit, scheduler) }
