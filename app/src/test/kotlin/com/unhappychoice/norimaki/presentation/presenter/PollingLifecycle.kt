package com.unhappychoice.norimaki.presentation.presenter

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.unhappychoice.norimaki.MainActivity
import io.mockk.every
import io.mockk.excludeRecords

internal class PollingLifecycle(activity: MainActivity) : LifecycleOwner {
    override val lifecycle = LifecycleRegistry.createUnsafe(this)

    init {
        every { activity.lifecycle } answers { this@PollingLifecycle.lifecycle }
        excludeRecords { activity.lifecycle }
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    fun start() = lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)
    fun stop() = lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
}
