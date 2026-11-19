package com.unhappychoice.norimaki.presentation.view

import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.github.unhappychoice.circleci.v1.response.Build
import com.unhappychoice.norimaki.MainActivity
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.infrastructure.testresults.TestFailure
import com.unhappychoice.norimaki.infrastructure.testresults.TestResults
import com.unhappychoice.norimaki.infrastructure.testresults.TestResultsClient
import com.unhappychoice.norimaki.presentation.screen.TestResultsScreen
import io.mockk.every
import io.mockk.mockk
import io.reactivex.Single
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.schedulers.TestScheduler
import io.reactivex.subjects.SingleSubject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TestResultsViewTest {
    @Before
    fun pauseBackgroundRequests() {
        val scheduler = TestScheduler()
        RxJavaPlugins.setIoSchedulerHandler { scheduler }
    }

    @After
    fun restoreScheduler() {
        RxJavaPlugins.reset()
    }

    @Test
    fun displaysFailuresAfterRetryingARequestError() {
        val pending = SingleSubject.create<TestResults>()
        val reported = TestResults.Reported(listOf(TestFailure("asserts", "Example", "Expected a value")))
        withResults(pending, Single.just(reported)) { view ->
            assertStatus(view, R.string.test_results_loading)
            pending.onError(IOException("Network unavailable"))
            shadowOf(Looper.getMainLooper()).idle()
            assertStatus(view, R.string.test_results_load_error)

            val retry = view.findViewById<Button>(R.id.retry)
            assertEquals(View.VISIBLE, retry.visibility)
            retry.performClick()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(View.GONE, retry.visibility)
            assertEquals(View.GONE, view.findViewById<TextView>(R.id.status).visibility)
            val list = view.findViewById<RecyclerView>(R.id.failures)
            assertEquals(1, requireNotNull(list.adapter).itemCount)
        }
    }

    @Test
    fun distinguishesUnavailableResultsFromReportedResultsWithNoFailures() {
        withResults(Single.just(TestResults.Unavailable)) { view ->
            assertStatus(view, R.string.test_results_unavailable)
        }
        withResults(Single.just(TestResults.Reported(emptyList()))) { view ->
            assertStatus(view, R.string.test_results_no_failures)
        }
    }

    @Test
    fun disposesPendingRequestWhenTheViewDetaches() {
        val pending = SingleSubject.create<TestResults>()
        withResults(pending) { assertTrue(pending.hasObservers()) }
        assertFalse(pending.hasObservers())
    }

    private fun withResults(vararg responses: Single<TestResults>, verify: (TestResultsView) -> Unit) {
        val build = mockk<Build>()
        val client = mockk<TestResultsClient> {
            every { fetch(build) } returnsMany responses.toList()
        }
        val screen = TestResultsScreen(build)
        Robolectric.buildActivity(MainActivity::class.java).use { controller ->
            val activity = controller.setup().get()
            val container = activity.binding.containerView
            val view = activity.layoutInflater.inflate(screen.getLayoutResource(), container, false) as TestResultsView
            view.di = screen.module(DI { bind<TestResultsClient>() with singleton { client } })
            view.presenter.di = view.di
            container.addView(view)
            shadowOf(Looper.getMainLooper()).idle()
            verify(view)
        }
    }

    private fun assertStatus(view: TestResultsView, message: Int) {
        val status = view.findViewById<TextView>(R.id.status)
        assertEquals(View.VISIBLE, status.visibility)
        assertEquals(view.context.getString(message), status.text.toString())
    }
}
