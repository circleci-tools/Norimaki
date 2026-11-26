package com.unhappychoice.norimaki.presentation.presenter.core

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposables

class StartedPollingTest : DescribeSpec({
    fun registry(): LifecycleRegistry {
        val owner = object : LifecycleOwner {
            lateinit var registry: LifecycleRegistry
            override val lifecycle get() = registry
        }
        return LifecycleRegistry.createUnsafe(owner).also {
            owner.registry = it
            it.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        }
    }

    it("subscribes on start by default and resubscribes on every restart") {
        var subscribeCount = 0
        val bag = CompositeDisposable()
        val lifecycle = registry()
        StartedPolling { subscribeCount++; Disposables.disposed() }.observe(lifecycle, bag)

        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)
        subscribeCount shouldBe 1

        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)
        subscribeCount shouldBe 2
    }

    it("skips the automatic refresh on restart once shouldSkip reports true") {
        var subscribeCount = 0
        var skip = false
        val bag = CompositeDisposable()
        val lifecycle = registry()
        StartedPolling(shouldSkip = { skip }) { subscribeCount++; Disposables.disposed() }.observe(lifecycle, bag)

        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)
        subscribeCount shouldBe 1

        skip = true
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)
        subscribeCount shouldBe 1

        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        skip = false
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)
        subscribeCount shouldBe 2
    }

    it("still subscribes an explicit refresh while started even when shouldSkip reports true") {
        var subscribeCount = 0
        val bag = CompositeDisposable()
        val lifecycle = registry()
        val polling = StartedPolling(shouldSkip = { true }) { subscribeCount++; Disposables.disposed() }
        polling.observe(lifecycle, bag)

        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)
        subscribeCount shouldBe 0

        polling.refresh()
        subscribeCount shouldBe 1
    }

    it("ignores refresh calls while stopped") {
        var subscribeCount = 0
        val bag = CompositeDisposable()
        val lifecycle = registry()
        val polling = StartedPolling { subscribeCount++; Disposables.disposed() }
        polling.observe(lifecycle, bag)

        polling.refresh()
        subscribeCount shouldBe 0
    }
})
