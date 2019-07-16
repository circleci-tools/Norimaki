package com.unhappychoice.norimaki.extension

import android.util.Log
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.Subject

fun <T: Any> Observable<T>.subscribeNext(fn: (T) -> Unit): Disposable =
    subscribeBy(
        onNext = { fn(it) },
        onError = { it.printStackTrace() },
        onComplete = {}
    )

fun <T : Any> Observable<T>.subscribeError(fn: (e: Throwable?) -> Unit): Disposable =
    subscribeBy(
        onNext = {},
        onError = { fn(it) },
        onComplete = {}
    )

fun <T : Any> Observable<T>.subscribeCompleted(fn: () -> Unit): Disposable =
    subscribeBy(
        onNext = {},
        onError = { it.printStackTrace() },
        onComplete = { fn() }
    )

fun <T> Observable<T>.subscribeOnIoObserveOnUI(): Observable<T> =
    subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())

fun <T> Observable<T?>.filterNotNull(): Observable<T> =
    filter { it != null }.map { it!! }

fun <T> Observable<T>.withLog(name: String = "Anonymous"): Observable<T> =
    doOnNext { Log.d(name, "onNext: ${it?.toString()}") }
        .doOnError { Log.d(name, "onError: $it") }
        .doOnComplete { Log.d(name, "onCompleted") }

fun <T : Any> Observable<T>.bindTo(subject: Subject<T>): Disposable = subscribeNext { subject.onNext(it) }
fun <T : Any> Observable<T>.bindTo(variable: Variable<T>): Disposable = subscribeNext { variable.value = it }
