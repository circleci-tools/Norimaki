package com.unhappychoice.norimaki.presentation.view

import android.app.Activity
import android.app.Application
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.extension.Variable
import com.unhappychoice.norimaki.presentation.log.BuildLog
import com.unhappychoice.norimaki.presentation.presenter.BuildStepPresenter
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.singleton
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class BuildStepViewTest {
    private val log = BuildLog()
    private val loadFailed = Variable(false)
    private val canRetry = Variable(false)

    @Test
    fun rendersBuildStepOutputAndShowsTheErrorWithoutClearingExistingLogs() {
        val presenter = mockk<BuildStepPresenter>(relaxUnitFun = true) {
            every { this@mockk.log } returns this@BuildStepViewTest.log
            every { logLoadFailed } returns loadFailed
            every { canRetryStepLoad } returns canRetry
        }

        Robolectric.buildActivity(Activity::class.java).use { controller ->
            val activity = controller.setup().get()
            val view = activity.layoutInflater.inflate(R.layout.build_step_view, null) as BuildStepView
            view.di = DI { bind<BuildStepPresenter>() with singleton { presenter } }
            activity.setContentView(view)
            val output = view.findViewById<RecyclerView>(R.id.logLines)
            val retry = view.findViewById<View>(R.id.retryStepLoad)
            val error = view.findViewById<TextView>(R.id.logLoadError)

            val chunks = PublishSubject.create<String>()
            val subscription = log.render(chunks, Schedulers.trampoline())
            try {
                chunks.onNext("Build out")
                assertEquals("Build out", renderOutput(output))
                chunks.onNext("put")
                assertEquals("Build output", renderOutput(output))
                assertEquals(View.GONE, error.visibility)
                assertEquals(View.GONE, retry.visibility)

                loadFailed.value = true

                assertEquals(View.VISIBLE, error.visibility)
                assertEquals(activity.getString(R.string.build_log_load_error), error.text.toString())
                assertEquals("Build output", renderOutput(output))
                assertEquals(View.GONE, retry.visibility)

                canRetry.value = true

                assertEquals(View.VISIBLE, retry.visibility)
                retry.performClick()
                verify(exactly = 1) { presenter.retryStepLoad() }

                canRetry.value = false
                loadFailed.value = false

                assertEquals(View.GONE, retry.visibility)
                assertEquals(View.GONE, error.visibility)

                log.clear()
                assertEquals(0, output.adapter?.itemCount)
            } finally {
                subscription.dispose()
            }
        }
    }

    private fun renderOutput(output: RecyclerView): String {
        val width = View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY)
        val height = View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY)
        output.measure(width, height)
        output.layout(0, 0, 480, 320)
        return (output.findViewHolderForAdapterPosition(0)?.itemView as TextView).text.toString()
    }
}
