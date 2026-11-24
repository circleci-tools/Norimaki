package com.unhappychoice.norimaki

import android.os.Looper
import android.widget.Button
import com.github.unhappychoice.circleci.v1.response.Build
import com.unhappychoice.norimaki.domain.model.BuildId
import com.unhappychoice.norimaki.presentation.core.ScreenTransition
import com.unhappychoice.norimaki.presentation.presenter.BUILD_LIST_PAGE_SIZE
import com.unhappychoice.norimaki.presentation.screen.BuildListScreen
import com.unhappychoice.norimaki.presentation.screen.BuildScreen
import com.unhappychoice.norimaki.presentation.view.BuildListView
import flow.Flow
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = BranchFilterTestApplication::class, sdk = [34])
class BuildListBranchNavigationTest {
    private val branch = "release/Next"

    @Before
    fun useSynchronousRequests() {
        RxJavaPlugins.setIoSchedulerHandler { Schedulers.trampoline() }
        RxJavaPlugins.setComputationSchedulerHandler { Schedulers.trampoline() }
    }

    @After
    fun restoreSchedulers() {
        RxJavaPlugins.reset()
    }

    @Test
    fun restoresTheSelectedBranchLabelAndRequestAfterOpeningABuildAndGoingBack() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            val app = activity.application as BranchFilterTestApplication
            Flow.get(activity).set(BuildListScreen("github/owner/repo"))
            finishTransition()

            currentBuildListView(activity).presenter.selectBranch(branch)
            finishTransition()
            assertEquals("Branch: $branch", branchFilterText(activity))
            verify(exactly = 1) {
                app.api.getBranchBuilds(
                    vcsType = "github", userName = "owner", project = "repo",
                    branch = branch, offset = 0, limit = BUILD_LIST_PAGE_SIZE
                )
            }
            clearMocks(app.api, answers = false)

            currentBuildListView(activity).presenter.goToBuildView(buildOf(number = 7))
            finishTransition()
            assertEquals(BuildScreen(BuildId("github", "owner", "repo", 7)), Flow.get(activity).history.top<Any>())

            activity.onBackPressedDispatcher.onBackPressed()
            finishTransition()

            assertEquals(BuildListScreen("github/owner/repo"), Flow.get(activity).history.top<Any>())
            assertEquals("Branch: $branch", branchFilterText(activity))
            verify(exactly = 1) {
                app.api.getBranchBuilds(
                    vcsType = "github", userName = "owner", project = "repo",
                    branch = branch, offset = 0, limit = BUILD_LIST_PAGE_SIZE
                )
            }
            verify(exactly = 0) {
                app.api.getProjectBuilds(
                    vcsType = "github", userName = "owner", project = "repo",
                    offset = 0, limit = BUILD_LIST_PAGE_SIZE
                )
            }
        }
    }

    @Test
    fun freshProjectListDefaultsToAllBranches() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            val app = activity.application as BranchFilterTestApplication
            Flow.get(activity).set(BuildListScreen("github/owner/other-repo"))
            finishTransition()

            assertEquals("Branch: All branches", branchFilterText(activity))
            verify(exactly = 1) {
                app.api.getProjectBuilds(
                    vcsType = "github", userName = "owner", project = "other-repo",
                    offset = 0, limit = BUILD_LIST_PAGE_SIZE
                )
            }
        }
    }

    private fun buildOf(number: Int): Build = mockk {
        every { vcsUrl } returns "https://github.com/owner/repo"
        every { username } returns "owner"
        every { reponame } returns "repo"
        every { buildNum } returns number
    }

    private fun currentBuildListView(activity: MainActivity): BuildListView =
        activity.binding.containerView.getChildAt(0) as BuildListView

    private fun branchFilterText(activity: MainActivity): String =
        currentBuildListView(activity).findViewById<Button>(R.id.branchFilter).text.toString()

    private fun finishTransition() {
        shadowOf(Looper.getMainLooper()).idleFor(ScreenTransition.DURATION_MILLIS * 2, TimeUnit.MILLISECONDS)
    }
}
