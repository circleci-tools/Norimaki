package com.unhappychoice.norimaki.presentation.view

import android.app.Activity
import android.app.Application
import android.os.Looper
import android.view.View
import android.widget.TextView
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.extension.Variable
import com.unhappychoice.norimaki.presentation.presenter.BuildStepPresenter
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.schedulers.TestScheduler
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.singleton
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class BuildStepViewTest {
    private val scheduler = TestScheduler()
    private val log = Variable("")
    private val loadFailed = Variable(false)
    private val canRetry = Variable(false)

    @Before
    fun useTestScheduler() {
        RxJavaPlugins.setIoSchedulerHandler { scheduler }
    }

    @After
    fun restoreSchedulers() {
        RxJavaPlugins.reset()
    }

    @Test
    fun rendersBuildStepOutputAndShowsTheErrorWithoutClearingExistingLogs() {
        val presenter = mockk<BuildStepPresenter>(relaxUnitFun = true) {
            every { logString } returns log
            every { logLoadFailed } returns loadFailed
            every { canRetryStepLoad } returns canRetry
        }

        Robolectric.buildActivity(Activity::class.java).use { controller ->
            val activity = controller.setup().get()
            val view = activity.layoutInflater.inflate(R.layout.build_step_view, null) as BuildStepView
            view.di = DI { bind<BuildStepPresenter>() with singleton { presenter } }
            activity.setContentView(view)
            val output = view.findViewById<TextView>(R.id.logText)
            val retry = view.findViewById<View>(R.id.retryStepLoad)
            val error = view.findViewById<TextView>(R.id.logLoadError)

            log.value = "<b>Build output</b>"
            renderUpdates()

            assertEquals("Build output", output.text.toString())
            assertEquals(View.GONE, error.visibility)
            assertEquals(View.GONE, retry.visibility)

            loadFailed.value = true
            renderUpdates()

            assertEquals(View.VISIBLE, error.visibility)
            assertEquals(activity.getString(R.string.build_log_load_error), error.text.toString())
            assertEquals("Build output", output.text.toString())
            assertEquals(View.GONE, retry.visibility)

            canRetry.value = true
            renderUpdates()

            assertEquals(View.VISIBLE, retry.visibility)
            retry.performClick()
            verify(exactly = 1) { presenter.retryStepLoad() }

            canRetry.value = false
            loadFailed.value = false
            renderUpdates()

            assertEquals(View.GONE, retry.visibility)
            assertEquals(View.GONE, error.visibility)
        }
    }

    private fun renderUpdates() {
        scheduler.triggerActions()
        shadowOf(Looper.getMainLooper()).idle()
    }
}
