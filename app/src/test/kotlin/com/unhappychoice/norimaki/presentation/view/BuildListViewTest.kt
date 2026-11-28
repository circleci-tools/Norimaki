package com.unhappychoice.norimaki.presentation.view

import android.app.Activity
import android.app.Application
import android.content.DialogInterface
import android.os.Looper
import android.view.LayoutInflater
import android.view.View.MeasureSpec
import android.widget.AutoCompleteTextView
import android.widget.FrameLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.recyclerview.widget.GridLayoutManager
import com.github.unhappychoice.circleci.CircleCIAPIClientV1_1
import com.github.unhappychoice.circleci.v1.response.Build
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import com.unhappychoice.norimaki.MainActivity
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.databinding.BuildListViewBinding
import com.unhappychoice.norimaki.domain.model.ProjectId
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
import org.robolectric.shadows.ShadowDialog
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
                verify(exactly = 3) { api.firstBuildPage() }
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
    fun projectBranchFilterOffersSuggestionsAndAppliesExactNames() {
        val presenter = mockk<BuildListPresenter>(relaxed = true)
        val selected = Variable("")
        every { presenter.canFilterByBranch } returns true
        every { presenter.selectedBranch } returns selected.asObservable()
        every { presenter.recentBranches } returns listOf("main", "release/Next")
        every { presenter.builds } returns Observable.empty()

        Robolectric.buildActivity(Activity::class.java).setup().use { controller ->
            val activity = controller.get().apply { setTheme(R.style.AppTheme) }
            val binding = BuildListViewBinding.inflate(activity.layoutInflater)
            binding.root.di = DI { bind<BuildListPresenter>() with singleton { presenter } }
            activity.setContentView(binding.root)

            assertTrue(binding.branchFilter.isShown)
            assertEquals("Branch: All branches", binding.branchFilter.text.toString())
            binding.branchFilter.performClick()
            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            val input = checkNotNull(dialog.findViewById<AutoCompleteTextView>(R.id.branchName))
            val apply = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
            assertEquals("release/Next", input.adapter.getItem(1))
            assertFalse(apply.isEnabled)
            input.setText("feature/Exact")
            apply.performClick()
            shadowOf(Looper.getMainLooper()).idle()
            verify(exactly = 1) { presenter.selectBranch("feature/Exact") }

            selected.value = "feature/Exact"
            assertEquals("Branch: feature/Exact", binding.branchFilter.text.toString())
            binding.branchFilter.performClick()
            (ShadowDialog.getLatestDialog() as AlertDialog).getButton(DialogInterface.BUTTON_NEUTRAL).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            verify(exactly = 1) { presenter.selectBranch(null) }
        }
    }

    @Test
    fun showsFlakyTestsMenuItemOnlyForASpecificProjectAndNavigatesOnSelection() {
        val presenter = mockk<BuildListPresenter>(relaxed = true)
        every { presenter.canFilterByBranch } returns true
        every { presenter.builds } returns Observable.empty()
        every { presenter.canRetryPage } returns Observable.empty()
        every { presenter.selectedBranch } returns Variable("").asObservable()
        every { presenter.recentBranches } returns emptyList()

        Robolectric.buildActivity(Activity::class.java).setup().use { controller ->
            val activity = controller.get().apply { setTheme(R.style.AppTheme) }
            val binding = BuildListViewBinding.inflate(activity.layoutInflater)
            binding.root.di = DI { bind<BuildListPresenter>() with singleton { presenter } }
            activity.setContentView(binding.root)

            val menu = PopupMenu(activity, binding.root).menu
            binding.root.onCreateOptionsMenu(menu)
            val flakyTests = menu.findItem(2)
            assertEquals("Flaky Tests", flakyTests.title.toString())
            binding.root.onOptionsItemSelected(flakyTests)
            verify(exactly = 1) { presenter.goToFlakyTests() }
        }
    }

    @Test
    fun hidesFlakyTestsMenuItemForTheAggregatedRecentBuildsFeed() {
        val presenter = mockk<BuildListPresenter>(relaxed = true)
        every { presenter.canFilterByBranch } returns false
        every { presenter.builds } returns Observable.empty()
        every { presenter.canRetryPage } returns Observable.empty()

        Robolectric.buildActivity(Activity::class.java).setup().use { controller ->
            val activity = controller.get().apply { setTheme(R.style.AppTheme) }
            val binding = BuildListViewBinding.inflate(activity.layoutInflater)
            binding.root.di = DI { bind<BuildListPresenter>() with singleton { presenter } }
            activity.setContentView(binding.root)

            val menu = PopupMenu(activity, binding.root).menu
            binding.root.onCreateOptionsMenu(menu)
            assertTrue(menu.children.none { it.title == "Flaky Tests" })
        }
    }

    @Test
    fun paginatesForScrollNotificationsNearTheEnd() {
        withBuildList { presenter, scroll ->
            scroll(0, 12, 3)
            verify(exactly = 0) { presenter.getBuilds() }

            scroll(0, 0, 8)
            verify(exactly = 1) { presenter.getBuilds() }
        }
    }

    @Test
    fun retriesFailedRecentBuildsPageWithoutScrolling() {
        assertPageRetry(null)
    }

    @Test
    fun retriesFailedProjectHistoryPageWithoutScrolling() {
        assertPageRetry(ProjectId("github", "owner", "repo"))
    }

    private fun assertPageRetry(projectId: ProjectId?) {
        val gson = GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create()
        val builds = (21 downTo 1).map { number ->
            gson.fromJson(
                """{"build_num":$number,"username":"owner","reponame":"repo","status":"success"}""",
                Build::class.java
            ).copy(queuedAt = Date(number.toLong()))
        }
        val firstPage = builds.take(20)
        val api = mockk<CircleCIAPIClientV1_1>()
        every { api.historyPage(projectId, 0) } returns Observable.just(firstPage)
        every { api.historyPage(projectId, 20) } returnsMany listOf(
            Observable.error(IllegalStateException("Page fetch failed")),
            Observable.just(builds.drop(20))
        )
        val scheduler = TestScheduler()
        val scope = MortarScope.buildRootScope()
            .withService(BundleServiceRunner.SERVICE_NAME, BundleServiceRunner())
            .build("test")

        RxJavaPlugins.setComputationSchedulerHandler { scheduler }
        RxJavaPlugins.setIoSchedulerHandler { scheduler }
        try {
            Robolectric.buildActivity(ComponentActivity::class.java).setup().use { controller ->
                val activity = controller.get()
                val view = buildListForReattachment(activity, scope, api, projectId)
                activity.setContentView(view)
                val binding = BuildListViewBinding.bind(view)
                val adapter = binding.buildsView.adapter as BuildAdapter
                adapter.submitList(null)
                val history = view.presenter.builds.test()
                drainRequests(scheduler)
                assertEquals(firstPage, adapter.currentList)
                assertFalse(binding.retryBuildPage.isVisible)

                val layout = object : GridLayoutManager(activity, 1) {
                    var lastVisible = firstPage.lastIndex
                    override fun findLastVisibleItemPosition() = lastVisible
                }
                binding.buildsView.layoutManager = layout
                dispatchScroll(binding, 0, 12)
                drainRequests(scheduler)
                assertTrue(binding.retryBuildPage.isVisible)
                assertEquals(firstPage, adapter.currentList)
                dispatchScroll(binding, 0, 0)
                scheduler.advanceTimeBy(BuildListPresenter.BUILD_LIST_POLLING_SECONDS, SECONDS)
                drainRequests(scheduler)
                verify(exactly = 3) { api.historyPage(projectId, 0) }
                verify(exactly = 1) { api.historyPage(projectId, 20) }
                assertTrue(binding.retryBuildPage.isVisible)

                layout.lastVisible = 0
                assertTrue(binding.retryBuildPage.performClick())
                drainRequests(scheduler)
                verify(exactly = 2) { api.historyPage(projectId, 20) }
                history.assertValues(emptyList(), firstPage, builds)
                assertFalse(binding.retryBuildPage.isVisible)
                history.dispose()
            }
        } finally {
            scope.destroy()
            RxJavaPlugins.reset()
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
        api: CircleCIAPIClientV1_1,
        projectId: ProjectId? = ProjectId("github", "owner", "repo")
    ): BuildListView {
        val context = scope.createContext(activity)
        val presenter = BuildListPresenter().apply {
            di = DI {
                bind<SessionService>() with singleton { testSession(api) }
                if (projectId != null) bind<ProjectId>() with singleton { projectId }
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

    private fun CircleCIAPIClientV1_1.historyPage(projectId: ProjectId?, offset: Int) =
        if (projectId == null) getRecentBuilds(offset = offset, limit = 20)
        else getProjectBuilds(vcsType = "github", userName = "owner", project = "repo", offset = offset, limit = 20)

    private fun dispatchScroll(binding: BuildListViewBinding, dx: Int, dy: Int) {
        ReflectionHelpers.callInstanceMethod<Unit>(
            binding.buildsView, "dispatchOnScrolled",
            ClassParameter.from(Integer.TYPE, dx),
            ClassParameter.from(Integer.TYPE, dy)
        )
    }

    private fun withBuildList(
        test: (BuildListPresenter, (dx: Int, dy: Int, lastVisible: Int) -> Unit) -> Unit
    ) {
        val presenter = mockk<BuildListPresenter>(relaxed = true)
        every { presenter.builds } returns Observable.empty()
        every { presenter.canRetryPage } returns Observable.just(false)

        Robolectric.buildActivity(Activity::class.java).setup().use { controller ->
            val activity = controller.get()
            val binding = BuildListViewBinding.inflate(activity.layoutInflater)
            binding.root.di = DI { bind<BuildListPresenter>() with singleton { presenter } }
            activity.setContentView(binding.root)

            assertFalse(binding.branchFilter.isShown)
            val recycler = binding.buildsView
            val layout = object : GridLayoutManager(activity, 1) {
                var lastVisible = 0
                override fun getItemCount() = 10
                override fun findLastVisibleItemPosition() = lastVisible
            }
            recycler.layoutManager = layout
            clearMocks(presenter, answers = false)

            test(presenter) { dx, dy, lastVisible ->
                layout.lastVisible = lastVisible
                dispatchScroll(binding, dx, dy)
            }
        }
    }
}
