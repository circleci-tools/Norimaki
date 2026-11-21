package com.unhappychoice.norimaki.presentation.view

import android.app.Activity
import android.content.DialogInterface
import android.os.Looper
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.TextView
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AlertDialog
import com.github.unhappychoice.circleci.v1.response.Build
import com.unhappychoice.norimaki.MainActivity
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.extension.Variable
import com.unhappychoice.norimaki.presentation.presenter.BuildPresenter
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.singleton
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BuildViewTest {
    private val presenter = mockk<BuildPresenter>(relaxUnitFun = true) {
        every { hasWorkflow() } returns false
        every { steps } returns Variable(emptyList())
        every { projectName } returns "github/owner/repo"
        every { rebuildFailed } returns Variable(false)
        every { buildLoadFailed } returns Variable(false)
    }

    @Test
    fun routesOnlyTheArtifactsMenuItemToArtifactNavigation() = withBuildView { activity, view ->
        view.onOptionsItemSelected(null)
        val menu = PopupMenu(activity, view).menu
        view.onCreateOptionsMenu(menu)
        menu.add(0, 100, 0, "Unrelated item")
        val items = (0 until menu.size()).map(menu::getItem)
        val artifacts = items.single { it.title == activity.getString(R.string.artifacts) }
        items.filter { it != artifacts }.forEach(view::onOptionsItemSelected)
        verify(exactly = 0) { presenter.goToArtifactsScreen() }

        view.onOptionsItemSelected(artifacts)
        verify(exactly = 1) { presenter.goToArtifactsScreen() }
    }

    @Test
    fun retriesLoadingTheBuildAfterFailure() = withBuildView { _, view ->
        val failed = presenter.buildLoadFailed
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

    @Test
    fun showsTheLoadedBuildTitleInTheActionBar() = withBuildView { _, view ->
        val actionBar = mockk<ActionBar>(relaxed = true)
        val host = mockk<MainActivity> { every { supportActionBar } returns actionBar }
        every { presenter.activity } returns host
        val build = mockk<Build> {
            every { branch } returns "main"
            every { buildNum } returns 42
            every { vcsRevision } returns "abcdef123"
        }

        view.showBuild(build)

        assertEquals("main #42 (abcdef)", view.screenTitle)
        verify { actionBar.title = "main #42 (abcdef)" }
    }

    @Test
    fun cancelLeavesTheBuildUntouched() = withCacheClearDialog { _, _, dialog ->
        val message = dialog.findViewById<TextView>(android.R.id.message)?.text.toString()
        assertTrue(message.contains("entire github/owner/repo project"))
        assertTrue(message.contains("Other builds can be affected even if the retry fails."))

        dialog.getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        verify(exactly = 0) { presenter.rebuildWithoutCache() }
        verify(exactly = 0) { presenter.rebuild() }
    }

    @Test
    fun confirmDispatchesTheCacheClearingRebuild() = withCacheClearDialog { _, _, dialog ->
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        verify(exactly = 1) { presenter.rebuildWithoutCache() }
    }

    @Test
    fun selectingCacheClearAgainKeepsTheExistingDialog() = withCacheClearDialog { view, item, dialog ->
        view.onOptionsItemSelected(item)

        assertSame(dialog, ShadowDialog.getLatestDialog())
        assertTrue(dialog.isShowing)
        verify(exactly = 0) { presenter.rebuildWithoutCache() }
    }

    @Test
    fun selectingCacheClearAfterCancellationOpensAnotherDialog() = withCacheClearDialog { view, item, dialog ->
        dialog.getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        view.onOptionsItemSelected(item)

        val reopened = ShadowDialog.getLatestDialog()
        assertNotSame(dialog, reopened)
        assertTrue(reopened.isShowing)
        verify(exactly = 0) { presenter.rebuildWithoutCache() }
    }

    @Test
    fun detachingTheBuildViewDismissesTheDialog() = withCacheClearDialog { view, _, dialog ->
        assertTrue(view.isAttachedToWindow)
        assertTrue(dialog.isShowing)

        (view.parent as ViewGroup).removeView(view)
        shadowOf(Looper.getMainLooper()).idle()

        assertFalse(view.isAttachedToWindow)
        assertFalse(dialog.isShowing)
        verify(exactly = 0) { presenter.rebuildWithoutCache() }
    }

    @Test
    fun creatingOptionsWithoutAMenuDoesNotDispatchARebuild() = withBuildView { _, view ->
        view.onCreateOptionsMenu(null)

        verify(exactly = 0) { presenter.rebuildWithoutCache() }
        verify(exactly = 0) { presenter.rebuild() }
    }

    private fun withCacheClearDialog(check: (BuildView, MenuItem, AlertDialog) -> Unit) =
        withBuildView { activity, view ->
            val menu = PopupMenu(activity, view).menu
            view.onCreateOptionsMenu(menu)
            val item = (0 until menu.size()).map(menu::getItem)
                .single { it.title == "Clear project cache and rebuild" }
            view.onOptionsItemSelected(item)

            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            assertEquals(item.title, dialog.findViewById<TextView>(androidx.appcompat.R.id.alertTitle)?.text)
            verify(exactly = 0) { presenter.rebuildWithoutCache() }
            check(view, item, dialog)
        }

    private fun withBuildView(check: (Activity, BuildView) -> Unit) {
        Robolectric.buildActivity(Activity::class.java).use { controller ->
            val activity = controller.setup().get().apply { setTheme(R.style.AppTheme) }
            val view = activity.layoutInflater.inflate(R.layout.build_view, null) as BuildView
            view.di = DI { bind<BuildPresenter>() with singleton { presenter } }
            activity.setContentView(view)
            check(activity, view)
        }
    }
}
