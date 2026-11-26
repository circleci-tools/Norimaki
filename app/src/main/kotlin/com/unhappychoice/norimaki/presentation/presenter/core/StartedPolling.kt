package com.unhappychoice.norimaki.presentation.presenter.core

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.disposables.Disposables
import io.reactivex.disposables.SerialDisposable

internal class StartedPolling(
    private val shouldSkip: () -> Boolean = { false },
    private val subscribe: () -> Disposable
) : DefaultLifecycleObserver {
    private val subscription = SerialDisposable()
    private var started = false

    fun observe(lifecycle: Lifecycle, bag: CompositeDisposable) {
        val registration = Disposables.fromAction {
            subscription.dispose()
            lifecycle.removeObserver(this)
        }
        if (bag.add(registration)) lifecycle.addObserver(this)
    }

    fun refresh() {
        if (!started) return
        val run = SerialDisposable()
        if (subscription.set(run)) run.set(subscribe())
    }

    override fun onStart(owner: LifecycleOwner) {
        started = true
        if (!shouldSkip()) refresh()
    }

    override fun onStop(owner: LifecycleOwner) {
        started = false
        subscription.set(null)
    }
}
