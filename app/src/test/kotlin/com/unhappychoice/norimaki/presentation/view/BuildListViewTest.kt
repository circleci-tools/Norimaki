package com.unhappychoice.norimaki.presentation.view

import android.app.Activity
import android.app.Application
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.View.MeasureSpec
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.github.unhappychoice.circleci.CircleCIAPIClientV1_1
import com.github.unhappychoice.circleci.v1.response.Build
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import com.unhappychoice.norimaki.MainActivity
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.databinding.BuildListViewBinding
import com.unhappychoice.norimaki.domain.service.SessionService
import com.unhappychoice.norimaki.extension.Variable
import com.unhappychoice.norimaki.extension.getTimeAgo
import com.unhappychoice.norimaki.presentation.adapter.BuildAdapter
import com.unhappychoice.norimaki.presentation.presenter.BuildListPresenter
import com.unhappychoice.norimaki.presentation.presenter.testSession
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.reactivex.Observable
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.schedulers.TestScheduler
import mortar.MortarScope
import mortar.bundler.BundleServiceRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.singleton
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystem
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import java.util.Calendar
import java.util.Date
import java.util.concurrent.TimeUnit.SECONDS

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class BuildListViewTest {
    @Test
    @Config(instrumentedPackages = ["com.unhappychoice.norimaki.extension"])
    fun visibleBuildUpdatesElapsedTimeAfterAnIdenticalPoll() {
        val queuedAt = Date(ShadowSystem.currentTimeMillis() - Calendar.getInstance().timeZone.rawOffset - 109_000L)
        val build = GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create()
            .fromJson("""{"build_num":1,"username":"owner","reponame":"repo","status":"success"}""", Build::class.java)
            .copy(queuedAt = queuedAt)
        val api = mockk<CircleCIAPIClientV1_1>()
        every { api.firstBuildPage() } returns Observable.just(listOf(build))
        every {
            api.getProjectBuilds(vcsType = "github", userName = "owner", project = "repo", offset = 20, limit = 20)
        } returns Observable.just(emptyList())
        val scheduler = TestScheduler()
        val mainLooper = shadowOf(Looper.getMainLooper())
        val scope = MortarScope.buildRootScope()
            .withService(BundleServiceRunner.SERVICE_NAME, BundleServiceRunner())
            .build("test")

        RxJavaPlugins.setComputationSchedulerHandler { scheduler }
        RxJavaPlugins.setIoSchedulerHandler { scheduler }
        try {
            Robolectric.buildActivity(ComponentActivity::class.java).setup().use { controller ->
                val activity = controller.get()
                val view = buildListForReattachment(activity, scope, api)
                activity.setContentView(view)
                val recycler = BuildListViewBinding.bind(view).buildsView
                // Keep the first populated submission synchronous for this clock-driven test.
                (recycler.adapter as BuildAdapter).submitList(null)
                drainRequests(scheduler)
                val size = MeasureSpec.makeMeasureSpec(600, MeasureSpec.EXACTLY)
                view.measure(size, size)
                view.layout(0, 0, 600, 600)
                val row = checkNotNull(recycler.getChildAt(0))
                val elapsed = row.findViewById<TextView>(R.id.createdAt)
                assertTrue(row.isShown)
                assertEquals("1m ago", elapsed.text.toString())

                scheduler.advanceTimeBy(10, SECONDS)
                drainRequests(scheduler)
                verify(exactly = 2) { api.firstBuildPage() }
                mainLooper.idleFor(12, SECONDS)

                assertEquals("2m ago", queuedAt.getTimeAgo())
                assertSame(row, recycler.getChildAt(0))
                assertEquals("2m ago", elapsed.text.toString())
            }
        } finally {
            scope.destroy()
            RxJavaPlugins.reset()
        }
    }

    @Test
    fun workflowListPaginatesOnlyForVerticalScrollNearTheEnd() {
        withBuildList(isWorkflow = true) { presenter, scroll, _ ->
            scroll(0, 12, 3)
            scroll(12, 0, 8)
            verify(exactly = 0) { presenter.getBuilds() }

            scroll(0, 12, 8)
            verify(exactly = 1) { presenter.getBuilds() }
        }
    }

    @Test
    fun regularListPaginatesForZeroDeltaNotificationNearTheEnd() {
        withBuildList(isWorkflow = false) { presenter, scroll, _ ->
            scroll(0, 0, 8)
            verify(exactly = 1) { presenter.getBuilds() }
        }
    }

    @Test
    fun regularListShowsRetryAndWaitsForAUserActionAfterFailure() {
        withBuildList(isWorkflow = false) { presenter, scroll, binding ->
            assertEquals(View.GONE, binding.retryPage.visibility)

            presenter.canRetryPage.value = true
            assertEquals(View.VISIBLE, binding.retryPage.visibility)
            scroll(0, 0, 8)
            verify(exactly = 0) { presenter.getBuilds() }

            assertTrue(binding.retryPage.performClick())
            verify(exactly = 1) { presenter.getBuilds() }
        }
    }

    @Test
    fun reattachingBuildListStartsOneImmediateFetchAndOneFetchEveryTenSeconds() {
        val api = mockk<CircleCIAPIClientV1_1>()
        every { api.firstBuildPage() } returns Observable.just(emptyList())
        val scheduler = TestScheduler()
        val scope = MortarScope.buildRootScope()
            .withService(BundleServiceRunner.SERVICE_NAME, BundleServiceRunner())
            .build("test")
        RxJavaPlugins.setComputationSchedulerHandler { scheduler }
        RxJavaPlugins.setIoSchedulerHandler { scheduler }
        try {
            Robolectric.buildActivity(ComponentActivity::class.java).setup().use { controller ->
                val activity = controller.get()
                val host = FrameLayout(activity)
                activity.setContentView(host)
                val view = buildListForReattachment(activity, scope, api)
                host.addView(view)
                drainRequests(scheduler)

                assertTrue(view.isAttachedToWindow)
                host.removeView(view)
                assertFalse(view.isAttachedToWindow)
                clearMocks(api, answers = false)
                scheduler.advanceTimeBy(30, SECONDS)
                verify(exactly = 0) { api.firstBuildPage() }

                host.addView(view)
                assertTrue(view.isAttachedToWindow)
                drainRequests(scheduler)
                verify(exactly = 1) { api.firstBuildPage() }
                scheduler.advanceTimeBy(9, SECONDS)
                verify(exactly = 1) { api.firstBuildPage() }
                scheduler.advanceTimeBy(1, SECONDS)
                drainRequests(scheduler)
                verify(exactly = 2) { api.firstBuildPage() }
                scheduler.advanceTimeBy(9, SECONDS)
                verify(exactly = 2) { api.firstBuildPage() }
                scheduler.advanceTimeBy(1, SECONDS)
                drainRequests(scheduler)
                verify(exactly = 3) { api.firstBuildPage() }
            }
        } finally {
            scope.destroy()
            RxJavaPlugins.reset()
        }
    }

    private fun drainRequests(scheduler: TestScheduler) {
        val mainLooper = shadowOf(Looper.getMainLooper())
        repeat(3) {
            scheduler.triggerActions()
            mainLooper.idle()
        }
    }

    private fun buildListForReattachment(
        activity: ComponentActivity,
        scope: MortarScope,
        api: CircleCIAPIClientV1_1
    ): BuildListView {
        val context = scope.createContext(activity)
        val presenter = BuildListPresenter().apply {
            di = DI {
                bind<SessionService>() with singleton { testSession(api) }
                bind<String>() with singleton { "github/owner/repo" }
                bind<MainActivity>() with singleton {
                    mockk { every { lifecycle } returns activity.lifecycle }
                }
            }
        }
        return BuildListViewBinding.inflate(LayoutInflater.from(context)).root.apply {
            di = DI { bind<BuildListPresenter>() with singleton { presenter } }
        }
    }

    private fun CircleCIAPIClientV1_1.firstBuildPage() =
        getProjectBuilds(vcsType = "github", userName = "owner", project = "repo", offset = 0, limit = 20)

    private fun withBuildList(
        isWorkflow: Boolean,
        test: (BuildListPresenter, (dx: Int, dy: Int, lastVisible: Int) -> Unit, BuildListViewBinding) -> Unit
    ) {
        val presenter = mockk<BuildListPresenter>(relaxed = true)
        every { presenter.isWorkflow } returns isWorkflow
        every { presenter.page } returns Variable(0)
        every { presenter.canRetryPage } returns Variable(false)
        every { presenter.builds } returns Observable.empty()

        Robolectric.buildActivity(Activity::class.java).setup().use { controller ->
            val activity = controller.get()
            val binding = BuildListViewBinding.inflate(activity.layoutInflater)
            binding.root.di = DI { bind<BuildListPresenter>() with singleton { presenter } }
            activity.setContentView(binding.root)

            val recycler = binding.buildsView
            val layout = object : GridLayoutManager(activity, 1) {
                var lastVisible = 0
                override fun getItemCount() = 10
                override fun findLastVisibleItemPosition() = lastVisible
            }
            recycler.layoutManager = layout
            clearMocks(presenter, answers = false)

            val scroll = { dx: Int, dy: Int, lastVisible: Int ->
                layout.lastVisible = lastVisible
                ReflectionHelpers.callInstanceMethod<Unit>(
                    recycler, "dispatchOnScrolled",
                    ClassParameter.from(Integer.TYPE, dx),
                    ClassParameter.from(Integer.TYPE, dy)
                )
            }
            test(presenter, scroll, binding)
        }
    }
}
