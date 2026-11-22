package com.unhappychoice.norimaki.presentation.view

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.unhappychoice.norimaki.MainActivity
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.domain.model.BuildId
import com.unhappychoice.norimaki.domain.service.SessionService
import com.unhappychoice.norimaki.infrastructure.testresults.TestFailure
import com.unhappychoice.norimaki.infrastructure.testresults.TestResults
import com.unhappychoice.norimaki.infrastructure.testresults.TestResultsClient
import com.unhappychoice.norimaki.presentation.presenter.testSession
import com.unhappychoice.norimaki.presentation.screen.TestResultsScreen
import io.mockk.every
import io.mockk.mockk
import io.reactivex.Observable
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.schedulers.TestScheduler
import io.reactivex.subjects.PublishSubject
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.factory
import org.kodein.di.singleton
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.concurrent.thread

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TestResultsViewTest {
    private val buildId = BuildId("github", "owner", "repo", 123)
    private val scheduler = TestScheduler()

    @Before
    fun pauseBackgroundRequests() {
        RxJavaPlugins.setIoSchedulerHandler { scheduler }
    }

    @After
    fun restoreScheduler() {
        RxJavaPlugins.reset()
    }

    @Test
    fun displaysFailuresAfterRetryingARequestError() {
        val pending = PublishSubject.create<TestResults>()
        val firstPage = TestResults.FailurePage(listOf(TestFailure("asserts", "Example", "Expected a value")))
        withResults(pending, Observable.just(firstPage, TestResults.Reported)) { view ->
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
    fun keepsVisibleFailuresWhenALaterPageFails() {
        val pages = PublishSubject.create<TestResults>()
        withResults(pages) { view ->
            pages.onNext(TestResults.FailurePage(listOf(TestFailure("asserts", null, null))))
            pages.onError(IOException("Second page failed"))
            shadowOf(Looper.getMainLooper()).idle()

            assertStatus(view, R.string.test_results_incomplete)
            assertEquals(View.VISIBLE, view.findViewById<Button>(R.id.retry).visibility)
            assertEquals(1, view.findViewById<RecyclerView>(R.id.failures).adapter?.itemCount)
        }
    }

    @Test
    fun showsNoFailuresOnlyAfterTheFinalPassingPage() {
        withPendingSecondPage("""{"result":"success"}""", """{"items":[{"result":"success"}]}""") { view, finish ->
            assertStatus(view, R.string.test_results_loading)
            finish()
            assertStatus(view, R.string.test_results_no_failures)
        }
    }

    @Test
    fun displaysFirstPageWhileSecondPageIsPendingAndAppendsLaterFailuresOnce() {
        withPendingSecondPage(
            """{"result":"failure","name":"first"}""",
            """{"items":[{"result":"failure","name":"second"}]}"""
        ) { view, finish ->
            val list = view.findViewById<RecyclerView>(R.id.failures)
            val adapter = requireNotNull(list.adapter)
            assertEquals(1, adapter.itemCount)
            assertStatus(view, R.string.test_results_loading)
            finish()
            assertEquals(adapter, list.adapter)
            assertEquals(2, adapter.itemCount)
            assertEquals(View.GONE, view.findViewById<TextView>(R.id.status).visibility)
        }
    }

    @Test
    fun cancelsThePendingSecondPageWhenTheViewDetaches() {
        withPendingSecondPage("""{"result":"failure","name":"first"}""", """{"items":[]}""", cancel = true) { view, _ ->
            (view.parent as ViewGroup).removeView(view)
        }
    }

    @Test
    fun distinguishesUnavailableResultsFromReportedResultsWithNoFailures() {
        withResults(Observable.just(TestResults.Unavailable)) { view ->
            assertStatus(view, R.string.test_results_unavailable)
        }
        withResults(Observable.just(TestResults.Reported)) { view ->
            assertStatus(view, R.string.test_results_no_failures)
        }
    }

    @Test
    fun disposesPendingRequestWhenTheViewDetaches() {
        val pending = PublishSubject.create<TestResults>()
        withResults(pending) { assertTrue(pending.hasObservers()) }
        assertFalse(pending.hasObservers())
    }

    @Test
    fun disposesPendingRequestWhenTheSessionChanges() {
        val pending = PublishSubject.create<TestResults>()
        val session = testSession(mockk())
        withResults(pending, session = session) {
            assertTrue(pending.hasObservers())
            session.replace("new-token").blockingGet()
            assertFalse(pending.hasObservers())
        }
    }

    private fun withPendingSecondPage(
        firstItem: String,
        secondBody: String,
        cancel: Boolean = false,
        verify: (TestResultsView, () -> Unit) -> Unit
    ) {
        val secondRequested = CountDownLatch(1)
        val releaseSecond = CountDownLatch(1)
        lateinit var secondCall: Call
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val body = if (request.url.queryParameter("page-token") == null) {
                """{"items":[$firstItem],"next_page_token":"next"}"""
            } else {
                secondCall = chain.call()
                secondRequested.countDown()
                if (!releaseSecond.await(10, SECONDS)) throw IOException("Timed out waiting for second page release")
                secondBody
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body(body.toResponseBody()).build()
        }.build()
        val client = TestResultsClient("test-token", http)
        withResults(client.fetch(buildId)) { view ->
            val worker = thread(isDaemon = true) { scheduler.triggerActions() }
            try {
                assertTrue(secondRequested.await(5, SECONDS))
                shadowOf(Looper.getMainLooper()).idle()
                verify(view) {
                    releaseSecond.countDown()
                    worker.join(5000)
                    assertFalse(worker.isAlive)
                    shadowOf(Looper.getMainLooper()).idle()
                }
                if (cancel) assertTrue(secondCall.isCanceled())
            } finally {
                releaseSecond.countDown()
                worker.join(5000)
            }
        }
    }

    private fun withResults(
        vararg responses: Observable<TestResults>,
        session: SessionService = testSession(mockk()),
        verify: (TestResultsView) -> Unit
    ) {
        val client = mockk<TestResultsClient> {
            every { fetch(buildId) } returnsMany responses.toList()
        }
        val screen = TestResultsScreen(buildId)
        Robolectric.buildActivity(MainActivity::class.java).use { controller ->
            val activity = controller.setup().get()
            val container = activity.binding.containerView
            val view = activity.layoutInflater.inflate(screen.getLayoutResource(), container, false) as TestResultsView
            view.di = screen.module(DI {
                bind<SessionService>() with singleton { session }
                bind<TestResultsClient>() with factory { token: String ->
                    assertEquals("test-token", token)
                    client
                }
            })
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
