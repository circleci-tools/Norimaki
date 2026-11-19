package com.unhappychoice.norimaki.presentation.view

import android.os.Looper
import android.view.View
import android.widget.PopupMenu
import android.widget.TextView
import com.github.unhappychoice.circleci.CircleCIAPIClientV1_1
import com.github.unhappychoice.circleci.v1.response.Build
import com.unhappychoice.norimaki.MainActivity
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.domain.service.SessionService
import com.unhappychoice.norimaki.infrastructure.preference.APITokenPreference
import com.unhappychoice.norimaki.presentation.presenter.testSession
import com.unhappychoice.norimaki.presentation.screen.BuildScreen
import com.unhappychoice.norimaki.presentation.screen.core.Screen
import flow.Flow
import io.mockk.every
import io.mockk.mockk
import io.reactivex.Observable
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.schedulers.Schedulers
import io.reactivex.schedulers.TestScheduler
import io.reactivex.subjects.PublishSubject
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.singleton
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BuildViewTest {
    private val build = mockk<Build> {
        every { vcsUrl } returns "https://github.com/owner/repo"
        every { username } returns "owner"
        every { reponame } returns "repo"
        every { buildNum } returns 123
        every { workflows } returns null
        every { branch } returns "main"
        every { vcsRevision } returns "abc123def"
    }
    private val api = mockk<CircleCIAPIClientV1_1> {
        every { getBuild("github", "owner", "repo", 123) } returns Observable.never()
    }

    @After
    fun restoreScheduler() {
        RxJavaPlugins.reset()
    }

    @Test
    fun showsRetryFailureAndHidesItWhenRebuildIsRetried() {
        val failed = PublishSubject.create<Build>()
        val retried = PublishSubject.create<Build>()
        every { api.retryBuild("github", "owner", "repo", 123) } returnsMany listOf(failed, retried)

        withBuildScreen { activity, view ->
            val error = view.findViewById<TextView>(R.id.rebuildError)
            assertEquals(View.GONE, error.visibility)
            view.selectAction("Rebuild")
            failed.onError(IllegalStateException("Retry failed"))
            assertRebuildError(activity, error)

            view.selectAction("Rebuild")
            assertEquals(View.GONE, error.visibility)
            retried.onNext(build)
            retried.onComplete()
            assertReturnedToBuildList(activity)
        }
    }

    @Test
    fun showsCacheDeletionFailureAndHidesItWhenCacheFreeRebuildIsRetried() {
        val failed = PublishSubject.create<Unit>()
        val retried = PublishSubject.create<Unit>()
        every { api.deleteCache("github", "owner", "repo") } returnsMany listOf(failed, retried)
        every { api.retryBuild("github", "owner", "repo", 123) } returns Observable.just(build)

        withBuildScreen { activity, view ->
            val error = view.findViewById<TextView>(R.id.rebuildError)
            view.selectAction("Rebuild without cache")
            failed.onError(IllegalStateException("Cache deletion failed"))
            assertRebuildError(activity, error)

            view.selectAction("Rebuild without cache")
            assertEquals(View.GONE, error.visibility)
            retried.onNext(Unit)
            retried.onComplete()
            assertReturnedToBuildList(activity)
        }
    }

    private fun withBuildScreen(test: (MainActivity, BuildView) -> Unit) {
        APITokenPreference(RuntimeEnvironment.getApplication()).token = "dummy-token"
        val paused = TestScheduler()
        RxJavaPlugins.setIoSchedulerHandler { paused }

        Robolectric.buildActivity(MainActivity::class.java).use { controller ->
            val activity = controller.setup().get()
            val buildScreen = BuildScreen(build)
            Flow.get(activity).set(object : Screen() {
                override fun getTitle() = buildScreen.getTitle()
                override fun getLayoutResource() = buildScreen.getLayoutResource()
                override fun module(activityModule: DI) = DI {
                    extend(buildScreen.module(activityModule), allowOverride = true)
                    bind<SessionService>(overrides = true) with singleton { testSession(api) }
                }
            })
            shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS)
            RxJavaPlugins.setIoSchedulerHandler { Schedulers.trampoline() }
            test(activity, activity.binding.containerView.getChildAt(0) as BuildView)
        }
    }

    private fun BuildView.selectAction(title: String) {
        val menu = PopupMenu(context, this).menu
        onCreateOptionsMenu(menu)
        val item = (0 until menu.size()).map(menu::getItem).single { it.title == title }
        onOptionsItemSelected(item)
    }

    private fun assertRebuildError(activity: MainActivity, error: TextView) {
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(error.isShown)
        assertEquals(activity.getString(R.string.build_rebuild_error), error.text.toString())
    }

    private fun assertReturnedToBuildList(activity: MainActivity) {
        val paused = TestScheduler()
        RxJavaPlugins.setIoSchedulerHandler { paused }
        shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS)
        val container = activity.binding.containerView
        assertEquals(1, container.childCount)
        assertTrue(container.getChildAt(0) is BuildListView)
    }
}
