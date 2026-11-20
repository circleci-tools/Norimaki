package com.unhappychoice.norimaki.presentation.adapter

import android.app.Application
import android.os.Looper
import androidx.recyclerview.widget.RecyclerView
import com.github.unhappychoice.circleci.v1.response.BuildAction
import com.github.unhappychoice.circleci.v1.response.BuildStep
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class BuildStepAdapterDiffTest {
    @Test
    fun calculatesDiffsInBackgroundAndDispatchesOnlyTheNewestSnapshotOnMain() {
        val pendingDiffs = mutableListOf<Runnable>()
        val adapter = BuildStepAdapter(RuntimeEnvironment.getApplication()) { pendingDiffs.add(it) }
        val mainLooper = Looper.getMainLooper()
        val diffThreads = mutableListOf<Thread>()
        val updates = mutableListOf<Pair<Int, Thread>>()
        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                updates.add(itemCount to Thread.currentThread())
            }

            override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
                updates.add(-itemCount to Thread.currentThread())
            }
        })

        adapter.submitSteps(listOf(BuildStep("Initial", emptyList())))
        runInBackground(pendingDiffs.removeAt(0))
        shadowOf(mainLooper).idle()
        updates.clear()

        adapter.submitSteps(listOf(trackedStep("Older", diffThreads)))
        adapter.submitSteps(listOf(trackedStep("Newest", diffThreads), BuildStep("Extra", emptyList())))
        assertEquals(emptyList<Thread>(), diffThreads)

        val newestWorker = runInBackground(pendingDiffs.removeAt(1))
        assertEquals(1, adapter.itemCount)
        assertEquals(emptyList<Pair<Int, Thread>>(), updates)

        shadowOf(mainLooper).idle()
        assertEquals(2, adapter.itemCount)
        assertEquals(listOf(-1 to mainLooper.thread, 2 to mainLooper.thread), updates)

        val olderWorker = runInBackground(pendingDiffs.removeAt(0))
        shadowOf(mainLooper).idle()
        assertEquals(listOf(newestWorker, olderWorker), diffThreads)
        assertEquals(2, adapter.itemCount)
        assertEquals(listOf(-1 to mainLooper.thread, 2 to mainLooper.thread), updates)
    }

    private fun trackedStep(name: String, diffThreads: MutableList<Thread>) =
        BuildStep(name, object : AbstractList<BuildAction>() {
            override val size: Int
                get() {
                    diffThreads.add(Thread.currentThread())
                    return 0
                }

            override fun get(index: Int): BuildAction = throw IndexOutOfBoundsException(index)
        })

    private fun runInBackground(diff: Runnable): Thread {
        val task = FutureTask(diff, Unit)
        val thread = Thread(task).apply { start() }
        task.get(5, TimeUnit.SECONDS)
        return thread
    }
}
