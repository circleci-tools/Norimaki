package com.unhappychoice.norimaki.extension

import android.os.Looper
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.util.ReflectionHelpers.ClassParameter
import org.robolectric.util.ReflectionHelpers.callInstanceMethod

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@LooperMode(LooperMode.Mode.PAUSED)
class AndroidMainSchedulerTest {
    @Test
    fun workerWaitsForTheMainLooperSyncBarrier() {
        val mainLooper = Looper.getMainLooper()
        val mainLooperShadow = shadowOf(mainLooper)
        mainLooperShadow.idle()
        val worker = androidMainScheduler().createWorker()
        var executionLooper: Looper? = null

        try {
            val queue = mainLooper.queue
            val barrier = callInstanceMethod<Int>(queue, "postSyncBarrier")
            try {
                worker.schedule { executionLooper = Looper.myLooper() }
                mainLooperShadow.idle()
                assertNull(executionLooper)
            } finally {
                callInstanceMethod<Unit>(
                    queue,
                    "removeSyncBarrier",
                    ClassParameter.from(Integer.TYPE, barrier),
                )
            }

            mainLooperShadow.idle()
            assertSame(mainLooper, executionLooper)
        } finally {
            worker.dispose()
        }
    }
}
