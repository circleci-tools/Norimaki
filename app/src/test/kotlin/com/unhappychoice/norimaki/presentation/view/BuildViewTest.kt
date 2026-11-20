package com.unhappychoice.norimaki.presentation.view

import android.app.Activity
import android.content.DialogInterface
import android.os.Looper
import android.widget.PopupMenu
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.presentation.presenter.BuildPresenter
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
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
        every { build } returns mockk {
            every { vcsUrl } returns "https://github.com/owner/repo"
            every { username } returns "owner"
            every { reponame } returns "repo"
        }
    }

    @Test
    fun cancelLeavesTheBuildUntouched() = withCacheClearDialog { dialog ->
        val message = dialog.findViewById<TextView>(android.R.id.message)?.text.toString()
        assertTrue(message.contains("entire github/owner/repo project"))
        assertTrue(message.contains("Other builds can be affected even if the retry fails."))

        dialog.getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        verify(exactly = 0) { presenter.rebuildWithoutCache() }
        verify(exactly = 0) { presenter.rebuild() }
    }

    @Test
    fun confirmDispatchesTheCacheClearingRebuild() = withCacheClearDialog { dialog ->
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        verify(exactly = 1) { presenter.rebuildWithoutCache() }
    }

    private fun withCacheClearDialog(check: (AlertDialog) -> Unit) {
        Robolectric.buildActivity(Activity::class.java).use { controller ->
            val activity = controller.setup().get().apply { setTheme(R.style.AppTheme) }
            val view = activity.layoutInflater.inflate(R.layout.build_view, null) as BuildView
            view.di = DI { bind<BuildPresenter>() with singleton { presenter } }
            val menu = PopupMenu(activity, view).menu
            view.onCreateOptionsMenu(menu)
            val item = (0 until menu.size()).map(menu::getItem)
                .single { it.title == "Clear project cache and rebuild" }
            view.onOptionsItemSelected(item)

            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            assertEquals(item.title, dialog.findViewById<TextView>(androidx.appcompat.R.id.alertTitle)?.text)
            verify(exactly = 0) { presenter.rebuildWithoutCache() }
            check(dialog)
        }
    }
}
