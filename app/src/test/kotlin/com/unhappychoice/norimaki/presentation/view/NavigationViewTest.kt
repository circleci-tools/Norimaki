package com.unhappychoice.norimaki.presentation.view

import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.github.unhappychoice.circleci.v1.response.Project
import com.unhappychoice.norimaki.BackTestApplication
import com.unhappychoice.norimaki.MainActivity
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.domain.service.SessionService
import com.unhappychoice.norimaki.extension.androidMainScheduler
import com.unhappychoice.norimaki.presentation.adapter.ProjectAdapter
import io.mockk.every
import io.mockk.mockk
import io.reactivex.Observable
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.schedulers.Schedulers
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.kodein.di.direct
import org.kodein.di.instance
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = BackTestApplication::class, sdk = [34])
class NavigationViewTest {
    private val application get() = RuntimeEnvironment.getApplication() as BackTestApplication

    @Before
    fun useSynchronousRequests() {
        RxJavaPlugins.setIoSchedulerHandler { Schedulers.trampoline() }
    }

    @After
    fun restoreSchedulers() {
        RxJavaPlugins.reset()
    }

    @Test
    fun attachesAndProcessesMainThreadWorkWhileReadingTheSavedToken() {
        val worker = Executors.newSingleThreadExecutor()
        val readStarted = CountDownLatch(1)
        val releaseRead = CountDownLatch(1)
        val projects = listOf(mockk<Project>(relaxed = true))
        application.projects = Observable.just(projects)
        application.credentialScheduler = Schedulers.from(worker)
        application.sessionObserverScheduler = androidMainScheduler()
        application.beforeReadToken = {
            check(Looper.myLooper() != Looper.getMainLooper())
            readStarted.countDown()
            check(releaseRead.await(10, SECONDS))
        }

        try {
            withPicker { picker ->
                assertTrue(readStarted.await(10, SECONDS))
                assertTrue(picker.isAttachedToWindow)
                var mainThreadRan = false
                Handler(Looper.getMainLooper()).post { mainThreadRan = true }
                drainUi()
                assertTrue(mainThreadRan)
                assertEquals(0, picker.projectAdapter.itemCount)

                releaseRead.countDown()
                worker.submit {}.get(10, SECONDS)
                worker.submit {}.get(10, SECONDS)
                drainUi()
                assertEquals(projects, picker.projectAdapter.projects.value)
                assertEquals(listOf("saved"), application.verifiedTokens)
            }
        } finally {
            releaseRead.countDown()
            worker.shutdownNow()
        }
    }

    @Test
    fun publishesTheReplacementOnMainAfterTheCredentialWriteCompletes() {
        val worker = Executors.newSingleThreadExecutor()
        val writeStarted = CountDownLatch(1)
        val releaseWrite = CountDownLatch(1)
        application.credentialScheduler = Schedulers.from(worker)
        application.sessionObserverScheduler = androidMainScheduler()
        application.beforeWriteToken = {
            check(Looper.myLooper() != Looper.getMainLooper())
            writeStarted.countDown()
            check(releaseWrite.await(10, SECONDS))
        }
        val session = application.module.direct.instance<SessionService>()
        val publications = session.changes
            .filter { it.toNullable() != null }
            .map { Triple(it.toNullable()?.token, application.savedToken, Looper.myLooper()) }
            .test()

        try {
            val replacement = session.replace("replacement").test()
            assertTrue(writeStarted.await(10, SECONDS))
            drainUi()
            assertFalse(session.hasVerifiedSession)
            replacement.assertNotComplete()
            publications.assertNoValues()

            releaseWrite.countDown()
            worker.submit {}.get(10, SECONDS)
            drainUi()
            replacement.assertComplete()
            publications.assertValue(Triple("replacement", "replacement", Looper.getMainLooper()))
        } finally {
            releaseWrite.countDown()
            publications.dispose()
            worker.shutdownNow()
        }
    }

    @Test
    fun retriesFailedProjectRequestWithTheVerifiedSession() {
        var requests = 0
        val projects = listOf(mockk<Project>(relaxed = true))
        application.projects = Observable.defer {
            if (++requests == 1) Observable.error(IOException("HTTP 503"))
            else Observable.just(projects)
        }

        withPicker { picker ->
            val retry = picker.findViewById<Button>(R.id.retryProjects)
            assertEquals(1, requests)
            assertEquals(View.VISIBLE, picker.errorVisibility)
            assertEquals(0, picker.projectAdapter.itemCount)

            assertTrue(retry.performClick())
            drainUi()

            assertEquals(2, requests)
            assertEquals(View.GONE, picker.errorVisibility)
            assertEquals(projects, picker.projectAdapter.projects.value)
            assertEquals(listOf("saved"), application.verifiedTokens)
        }
    }

    @Test
    fun successfulEmptyResultShowsAnEmptyPickerWithoutAnError() {
        application.projects = Observable.just(emptyList())

        withPicker { picker ->
            assertEquals(0, picker.projectAdapter.itemCount)
            assertEquals(View.GONE, picker.errorVisibility)
            assertEquals(listOf("saved"), application.verifiedTokens)
        }
    }

    @Test
    fun matchingProjectNamesShowTheirProviderAndOpenItsBuildHistory() {
        val projects = listOf(project("github.com"), project("bitbucket.org"))
        application.projects = Observable.just(projects)

        val labels = listOf("GitHub · team/repo", "Bitbucket · team/repo")
        val providers = listOf("github", "bitbucket")
        providers.forEachIndexed { position, provider ->
            withPicker { picker ->
                val rows = picker.findViewById<RecyclerView>(R.id.projectsView)
                val size = View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY)
                rows.measure(size, size)
                rows.layout(0, 0, 1000, 1000)

                val row = checkNotNull(rows.findViewHolderForAdapterPosition(position)).itemView
                assertEquals(labels[position], row.findViewById<TextView>(R.id.projectNameView).text.toString())
                assertTrue(row.performClick())
                drainUi()
            }
            assertEquals(Triple(provider, "team", "repo"), application.projectBuildRequests.last())
        }
    }

    private fun project(host: String): Project = mockk {
        every { vcsUrl } returns "https://$host/team/repo"
        every { username } returns "team"
        every { reponame } returns "repo"
    }

    private fun withPicker(test: (NavigationView) -> Unit) {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            val picker = activity.binding.navigationView
            activity.binding.drawerLayout.openDrawer(picker, false)
            drainUi()
            test(picker)
        }
    }

    private val NavigationView.errorVisibility get() = findViewById<View>(R.id.projectLoadError).visibility
    private val NavigationView.projectAdapter
        get() = findViewById<RecyclerView>(R.id.projectsView).adapter as ProjectAdapter

    private fun drainUi() = shadowOf(Looper.getMainLooper()).idle()
}
