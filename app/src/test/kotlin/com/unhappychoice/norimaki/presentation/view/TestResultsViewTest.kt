package com.unhappychoice.norimaki.presentation.view

import android.os.Bundle
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.unhappychoice.norimaki.ArtifactDownloadTestApplication
import com.unhappychoice.norimaki.MainActivity
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.domain.model.BuildId
import com.unhappychoice.norimaki.domain.service.SessionService
import com.unhappychoice.norimaki.infrastructure.testresults.TestFailure
import com.unhappychoice.norimaki.infrastructure.testresults.TestResults
import com.unhappychoice.norimaki.infrastructure.testresults.TestResultsClient
import com.unhappychoice.norimaki.presentation.core.ScreenTransition
import com.unhappychoice.norimaki.presentation.presenter.TestResultsPresenter
import com.unhappychoice.norimaki.presentation.presenter.core.PresenterNeedsToken
import com.unhappychoice.norimaki.presentation.presenter.testSession
import com.unhappychoice.norimaki.presentation.screen.APITokenScreen
import com.unhappychoice.norimaki.presentation.screen.TestResultsScreen
import flow.Flow
import io.mockk.every
import io.mockk.mockk
import io.reactivex.Observable
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.schedulers.TestScheduler
import io.reactivex.subjects.PublishSubject
import mortar.MortarScope
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
import java.util.concurrent.TimeUnit.MILLISECONDS
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
    @Config(application = ArtifactDownloadTestApplication::class)
    fun opensTokenSetupWhenTheRestoredSessionIsRejected() {
        val savedState = Bundle()
        val mainLooper = shadowOf(Looper.getMainLooper())
        val original = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = original.get()
        val app = activity.application as ArtifactDownloadTestApplication
        Flow.get(activity).set(TestResultsScreen(buildId))
        mainLooper.idleFor(ScreenTransition.DURATION_MILLIS * 2, MILLISECONDS)
        original.pause().stop().saveInstanceState(savedState).destroy()
        val verification = app.resetSession("revoked-token")

        Robolectric.buildActivity(MainActivity::class.java).setup(savedState).use { controller ->
            val restored = controller.get()
            val flow = Flow.get(restored)
            assertEquals(TestResultsScreen(buildId), flow.history.top<Any>())
            verification.onError(IOException("HTTP 401"))
            mainLooper.idle()
            assertEquals(View.GONE, restored.findViewById<Button>(R.id.retry).visibility)

            mainLooper.idleFor(500 + ScreenTransition.DURATION_MILLIS * 2, MILLISECONDS)
            assertTrue(flow.history.top<Any>() is APITokenScreen)
        }
    }

    @Test
    fun displaysFailuresAfterRetryingAnHttp500RequestError() {
        val statuses = listOf(500, 200).iterator()
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(statuses.next()).message("Test response")
                .body("""{"items":[{"result":"failure","name":"asserts"}]}""".toResponseBody())
                .build()
        }.build()
        withResults(TestResultsClient("test-token", http).fetch(buildId)) { view ->
            assertStatus(view, R.string.test_results_loading)
            scheduler.triggerActions()
            shadowOf(Looper.getMainLooper()).idle()
            assertStatus(view, R.string.test_results_load_error)

            val retry = view.findViewById<Button>(R.id.retry)
            assertEquals(View.VISIBLE, retry.visibility)
            retry.performClick()
            scheduler.triggerActions()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(View.GONE, retry.visibility)
            assertEquals(View.GONE, view.findViewById<TextView>(R.id.status).visibility)
            val list = view.findViewById<RecyclerView>(R.id.failures)
            assertEquals(1, requireNotNull(list.adapter).itemCount)
        }
    }

    @Test
    fun keepsVisibleFailuresWhenALaterPageFailsAndClearsThemOnRetry() {
        val pages = PublishSubject.create<TestResults>()
        val firstPage = TestResults.FailurePage(listOf(TestFailure("asserts", null, null)))
        withResults(pages, Observable.just(firstPage, TestResults.Reported)) { view ->
            pages.onNext(firstPage)
            pages.onError(IOException("Second page failed"))
            shadowOf(Looper.getMainLooper()).idle()

            assertStatus(view, R.string.test_results_incomplete)
            val retry = view.findViewById<Button>(R.id.retry)
            val failures = view.findViewById<RecyclerView>(R.id.failures)
            assertEquals(View.VISIBLE, retry.visibility)
            assertEquals(1, failures.adapter?.itemCount)

            retry.performClick()
            shadowOf(Looper.getMainLooper()).idle()

            assertEquals(1, failures.adapter?.itemCount)
            assertEquals(View.GONE, retry.visibility)
            assertEquals(View.GONE, view.findViewById<TextView>(R.id.status).visibility)
        }
    }

    @Test
    fun showsIncompleteWhenALaterPageFailsWithoutAnyTestFailures() {
        val pages = PublishSubject.create<TestResults>()
        withResults(pages) { view ->
            pages.onNext(TestResults.FailurePage(emptyList()))
            pages.onError(IOException("Second page failed"))
            shadowOf(Looper.getMainLooper()).idle()

            assertStatus(view, R.string.test_results_incomplete)
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

    @Test
    fun reloadsResultsWhenTheSameViewReattaches() {
        val pending = PublishSubject.create<TestResults>()
        withResults(pending, Observable.just(TestResults.Unavailable)) { view ->
            val container = view.parent as ViewGroup
            container.removeView(view)
            assertFalse(pending.hasObservers())
            container.addView(view)
            shadowOf(Looper.getMainLooper()).idle()
            assertStatus(view, R.string.test_results_unavailable)
        }
    }

    @Test
    fun ignoresRetryAfterTheSessionChanges() {
        val session = testSession(mockk())
        val failure = Observable.error<TestResults>(IOException("HTTP 500"))
        withResults(failure, session = session) { view ->
            assertStatus(view, R.string.test_results_load_error)
            session.replace("new-token").blockingGet()

            assertTrue(view.findViewById<Button>(R.id.retry).performClick())
            assertStatus(view, R.string.test_results_load_error)
        }
    }

    @Test
    fun skipsFetchingWhenAuthenticationFinishesBeforeAViewAttaches() {
        val session = testSession(mockk())
        val errors = mutableListOf<Throwable>()
        RxJavaPlugins.setErrorHandler { errors.add(it) }
        val presenter = TestResultsPresenter().apply {
            di = DI { bind<SessionService>() with singleton { session } }
        }
        val scope = MortarScope.buildRootScope().build("test-results")
        try {
            PresenterNeedsToken::class.java.getDeclaredMethod("onEnterScope", MortarScope::class.java)
                .apply { isAccessible = true }
                .invoke(presenter, scope)
            assertTrue(session.hasVerifiedSession)
            assertTrue(errors.toString(), errors.isEmpty())
        } finally {
            PresenterNeedsToken::class.java.getDeclaredMethod("onExitScope")
                .apply { isAccessible = true }
                .invoke(presenter)
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
