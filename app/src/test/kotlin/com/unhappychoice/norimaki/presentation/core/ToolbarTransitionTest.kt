package com.unhappychoice.norimaki.presentation.core

import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.PopupMenu
import androidx.core.view.children
import com.github.unhappychoice.circleci.CircleCIAPIClientV1_1
import com.github.unhappychoice.circleci.v1.response.Build
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import com.unhappychoice.norimaki.BackTestApplication
import com.unhappychoice.norimaki.MainActivity
import com.unhappychoice.norimaki.domain.model.BuildId
import com.unhappychoice.norimaki.domain.service.SessionService
import com.unhappychoice.norimaki.presentation.presenter.testSession
import com.unhappychoice.norimaki.presentation.screen.APITokenScreen
import com.unhappychoice.norimaki.presentation.screen.BuildScreen
import com.unhappychoice.norimaki.presentation.screen.WorkflowScreen
import com.unhappychoice.norimaki.presentation.screen.core.Screen
import com.unhappychoice.norimaki.presentation.view.BuildListView
import com.unhappychoice.norimaki.presentation.view.BuildView
import flow.Flow
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.reactivex.Observable
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.schedulers.TestScheduler
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
import java.util.concurrent.TimeUnit.MILLISECONDS

@RunWith(RobolectricTestRunner::class)
@Config(application = BackTestApplication::class, sdk = [34])
class ToolbarTransitionTest {
    private val pausedIo = TestScheduler()
    private val retryIo = TestScheduler()
    private val build = GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create().fromJson(
            """{"username":"owner","reponame":"repo","build_num":42,"branch":"main","vcs_revision":"abc123456","lifecycle":"finished","workflows":{"workflow_id":"workflow"}}""",
            Build::class.java
        )
    private val api = mockk<CircleCIAPIClientV1_1> {
        every { getBuild("github", "owner", "repo", 42) } returns Observable.just(build)
        every { retryBuild("github", "owner", "repo", 42) } returns Observable.never()
    }

    @Before
    fun setUp() {
        RxJavaPlugins.setIoSchedulerHandler { pausedIo }
    }

    @After
    fun tearDown() {
        RxJavaPlugins.reset()
    }

    @Test
    fun keepsScreenActionWithItsOwnerDuringBackAndRejectsItAfterCommit() {
        Robolectric.buildActivity(MainActivity::class.java).use { controller ->
            val activity = controller.setup().get()
            val flow = Flow.get(activity)
            flow.set(buildScreen())
            pausedIo.triggerActions()
            finishAnimation()
            assertEquals("main #42 (abc123)", activity.supportActionBar?.title)

            activity.onOptionsItemSelected(activity.menuItem("Show workflow jobs"))
            finishAnimation()
            assertTrue(flow.history.top<Any>() is WorkflowScreen)
            val changeToken = activity.menuItem("Change API Token")

            assertTrue(flow.goBack())
            val container = activity.binding.containerView
            assertEquals(2, container.childCount)
            assertTrue(container.getChildAt(0) is BuildView)
            assertTrue(container.getChildAt(1) is BuildListView)
            dispatch(activity, changeToken)
            verify(exactly = 0) { api.retryBuild(any(), any(), any(), any()) }

            finishAnimation()
            finishAnimation()
            assertTrue(flow.history.top<Any>() is APITokenScreen)

            assertTrue(flow.goBack())
            finishAnimation()
            val rebuild = activity.menuItem("Rebuild")
            assertEquals(changeToken.itemId, rebuild.itemId)

            dispatch(activity, changeToken)
            verify(exactly = 0) { api.retryBuild(any(), any(), any(), any()) }
            dispatch(activity, rebuild)
            verify(exactly = 1) { api.retryBuild("github", "owner", "repo", 42) }
        }
    }

    @Test
    fun homeItemReturnsToThePreviousScreen() {
        Robolectric.buildActivity(MainActivity::class.java).use { controller ->
            val activity = controller.setup().get()
            val flow = Flow.get(activity)
            val previousScreen = flow.history.top<Screen>()
            flow.set(buildScreen())
            finishAnimation()

            val home = activity.newMenu().add(Menu.NONE, android.R.id.home, Menu.NONE, "Home")
            assertTrue(activity.onOptionsItemSelected(home))
            finishAnimation()
            assertEquals(previousScreen, flow.history.top<Screen>())
        }
    }

    @Test
    fun leavesPreexistingItemsOutOfTheScreenMenuEvenWhenTheirIdsMatch() {
        Robolectric.buildActivity(MainActivity::class.java).use { controller ->
            val activity = controller.setup().get()
            val menu = activity.newMenu()
            val existingItem = menu.add("Existing action")

            assertTrue(activity.onCreateOptionsMenu(menu))
            val screenItem = menu.children.single { it.title == "Change API Token" }
            assertEquals(screenItem.itemId, existingItem.itemId)
            assertFalse(activity.onOptionsItemSelected(existingItem))
            assertTrue(activity.onOptionsItemSelected(screenItem))
            finishAnimation()
            assertTrue(Flow.get(activity).history.top<Any>() is APITokenScreen)
        }
    }

    @Test
    fun clearsScreenActionsWhenTheMenuIsNullOrTheOwnerHasNoMenu() {
        Robolectric.buildActivity(MainActivity::class.java).use { controller ->
            val activity = controller.setup().get()
            val menu = activity.newMenu()
            assertTrue(activity.onCreateOptionsMenu(menu))
            val oldItem = menu.getItem(0)

            assertTrue(activity.onCreateOptionsMenu(null))
            assertFalse(activity.onOptionsItemSelected(oldItem))

            val rebuiltMenu = activity.newMenu()
            assertTrue(activity.onCreateOptionsMenu(rebuiltMenu))
            val rebuiltItem = rebuiltMenu.getItem(0)
            activity.setMenuOwner(View(activity))
            assertFalse(activity.onOptionsItemSelected(rebuiltItem))

            val emptyMenu = activity.newMenu()
            assertTrue(activity.onCreateOptionsMenu(emptyMenu))
            assertEquals(0, emptyMenu.size())
        }
    }

    private fun MainActivity.newMenu(): Menu = PopupMenu(this, binding.toolbar).menu

    private fun dispatch(activity: MainActivity, item: MenuItem) {
        RxJavaPlugins.setIoSchedulerHandler { retryIo }
        try {
            activity.onOptionsItemSelected(item)
            retryIo.triggerActions()
        } finally {
            RxJavaPlugins.setIoSchedulerHandler { pausedIo }
        }
    }

    private fun MainActivity.menuItem(title: String): MenuItem =
        binding.toolbar.menu.children.single { it.title == title }

    private fun finishAnimation() {
        shadowOf(Looper.getMainLooper()).idleFor(ScreenTransition.DURATION_MILLIS + 100, MILLISECONDS)
    }

    private fun buildScreen(): Screen {
        val screen = BuildScreen(checkNotNull(BuildId.from(build)))
        return object : Screen() {
            override fun getTitle() = screen.getTitle()
            override fun getLayoutResource() = screen.getLayoutResource()
            override fun module(activityModule: DI) = DI {
                extend(screen.module(activityModule), allowOverride = true)
                bind<SessionService>(overrides = true) with singleton { testSession(api) }
            }
        }
    }
}
