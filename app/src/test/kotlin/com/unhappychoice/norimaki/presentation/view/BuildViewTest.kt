package com.unhappychoice.norimaki.presentation.view

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.widget.PopupMenu
import androidx.appcompat.app.ActionBar
import com.github.unhappychoice.circleci.v1.response.Build
import com.github.unhappychoice.circleci.v1.response.BuildStep
import com.unhappychoice.norimaki.MainActivity
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.extension.Variable
import com.unhappychoice.norimaki.presentation.presenter.BuildPresenter
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.singleton
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BuildViewTest {
    @Test
    fun routesOnlyTheArtifactsMenuItemToArtifactNavigation() {
        val presenter = mockk<BuildPresenter>(relaxed = true)
        Robolectric.buildActivity(Activity::class.java).use { controller ->
            val activity = controller.setup().get().apply { setTheme(R.style.AppTheme) }
            val view = LayoutInflater.from(activity).inflate(R.layout.build_view, null) as BuildView
            view.di = DI { bind<BuildPresenter>() with singleton { presenter } }

            view.onCreateOptionsMenu(null)
            view.onOptionsItemSelected(null)
            val menu = PopupMenu(activity, view).menu
            view.onCreateOptionsMenu(menu)
            val items = (0 until menu.size()).map(menu::getItem)
            val artifacts = items.single { it.title == activity.getString(R.string.artifacts) }
            items.filter { it != artifacts }.forEach(view::onOptionsItemSelected)
            verify(exactly = 0) { presenter.goToArtifactsScreen() }

            view.onOptionsItemSelected(artifacts)
            verify(exactly = 1) { presenter.goToArtifactsScreen() }
        }
    }

    @Test
    fun retriesLoadingTheBuildAfterFailure() {
        val failed = Variable(false)
        val presenter = mockk<BuildPresenter>(relaxed = true) {
            every { buildLoadFailed } returns failed
            every { steps } returns Variable(emptyList<BuildStep>())
        }
        Robolectric.buildActivity(Activity::class.java).use { controller ->
            val activity = controller.setup().get()
            activity.setTheme(R.style.AppTheme)
            val view = LayoutInflater.from(activity).inflate(R.layout.build_view, null) as BuildView
            view.di = DI { bind<BuildPresenter>() with singleton { presenter } }
            activity.setContentView(view)

            val error = view.findViewById<View>(R.id.buildLoadError)
            val retry = view.findViewById<View>(R.id.retryBuildLoad)
            assertEquals(View.GONE, error.visibility)
            retry.performClick()
            verify(exactly = 0) { presenter.pollBuild() }

            failed.value = true
            assertEquals(View.VISIBLE, error.visibility)
            retry.performClick()
            verify(exactly = 1) { presenter.pollBuild() }

            failed.value = false
            assertEquals(View.GONE, error.visibility)
        }
    }

    @Test
    fun showsTheLoadedBuildTitleInTheActionBar() {
        val actionBar = mockk<ActionBar>(relaxed = true)
        val host = mockk<MainActivity> { every { supportActionBar } returns actionBar }
        val presenter = mockk<BuildPresenter>(relaxed = true) {
            every { activity } returns host
            every { buildLoadFailed } returns Variable(false)
            every { steps } returns Variable(emptyList<BuildStep>())
        }
        val build = mockk<Build> {
            every { branch } returns "main"
            every { buildNum } returns 42
            every { vcsRevision } returns "abcdef123"
        }
        Robolectric.buildActivity(Activity::class.java).use { controller ->
            val activity = controller.setup().get().apply { setTheme(R.style.AppTheme) }
            val view = LayoutInflater.from(activity).inflate(R.layout.build_view, null) as BuildView
            view.di = DI { bind<BuildPresenter>() with singleton { presenter } }
            activity.setContentView(view)

            view.showBuild(build)

            assertEquals("main #42 (abcdef)", view.screenTitle)
            verify { actionBar.title = "main #42 (abcdef)" }
        }
    }
}
