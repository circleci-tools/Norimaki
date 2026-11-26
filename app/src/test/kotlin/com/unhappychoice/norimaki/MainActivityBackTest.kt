package com.unhappychoice.norimaki

import android.os.Looper
import android.view.MenuItem
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import androidx.core.view.children
import com.unhappychoice.norimaki.domain.model.ProjectId
import com.unhappychoice.norimaki.presentation.core.ScreenTransition
import com.unhappychoice.norimaki.presentation.screen.APITokenScreen
import com.unhappychoice.norimaki.presentation.screen.BuildListScreen
import com.unhappychoice.norimaki.presentation.view.APITokenView
import com.unhappychoice.norimaki.presentation.view.BuildListView
import flow.Direction
import flow.Flow
import flow.History
import io.mockk.every
import io.mockk.mockk
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = BackTestApplication::class, sdk = [34])
class MainActivityBackTest {
    @Test
    fun systemBackKeepsTokenFormDuringVerificationAndAfterRejection() =
        assertBackKeepsTokenForm { onBackPressedDispatcher.onBackPressed() }

    @Test
    fun toolbarBackKeepsTokenFormDuringVerificationAndAfterRejection() =
        assertBackKeepsTokenForm { clickToolbarBack() }

    @Test
    fun homeMenuBackKeepsTokenFormDuringVerificationAndAfterRejection() {
        val home = mockk<MenuItem> { every { itemId } returns android.R.id.home }
        assertBackKeepsTokenForm { onOptionsItemSelected(home) }
    }

    @Test
    fun toolbarBackReturnsToPreviousScreenBeforeSubmitting() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            val history = activity.openTokenForm()
            activity.clickToolbarBack()
            finishTransition()

            assertEquals(history.peek<BuildListScreen>(1), Flow.get(activity).history.top<Any>())
            assertTrue(activity.binding.containerView.children.single() is BuildListView)
        }
    }

    @Test
    fun systemBackFinishesWhenTokenFormIsTheOnlyScreen() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            Flow.get(activity).replaceHistory(APITokenScreen(), Direction.REPLACE)
            finishTransition()

            activity.onBackPressedDispatcher.onBackPressed()

            assertTrue(activity.isFinishing)
        }
    }

    private fun assertBackKeepsTokenForm(pressBack: MainActivity.() -> Unit) {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            val application = activity.application as BackTestApplication
            val history = activity.openTokenForm()
            val form = activity.binding.containerView.children.single() as APITokenView
            val field = form.findViewById<EditText>(R.id.accessTokenTextView)
            val submit = form.findViewById<Button>(R.id.submitButton)

            field.setText("rejected")
            assertTrue(submit.performClick())
            assertFalse(submit.isEnabled)
            activity.pressBack()
            assertTokenFormStayed(activity, form, history)

            application.rejection.onError(IllegalArgumentException("Rejected token"))
            assertEquals(activity.getString(R.string.api_token_verification_failed), field.error.toString())
            activity.pressBack()
            assertTokenFormStayed(activity, form, history)
            assertEquals(listOf("saved", "rejected"), application.verifiedTokens)

            field.setText("replacement")
            assertTrue(submit.performClick())
            finishTransition()
            assertEquals(listOf(BuildListScreen(null)), Flow.get(activity).history.toList())
            assertTrue(activity.binding.containerView.children.single() is BuildListView)
            assertEquals(listOf("saved", "rejected", "replacement"), application.verifiedTokens)
        }
    }

    private fun MainActivity.openTokenForm(): History {
        val flow = Flow.get(this)
        val history = flow.history.buildUpon()
            .push(BuildListScreen(ProjectId("github", "example", "project")))
            .push(APITokenScreen())
            .build()
        flow.setHistory(history, Direction.FORWARD)
        finishTransition()
        assertEquals(3, flow.history.size())
        return flow.history
    }

    private fun MainActivity.clickToolbarBack() {
        val button = binding.toolbar.children.filterIsInstance<ImageButton>().single()
        assertTrue(button.isShown)
        assertTrue(button.performClick())
    }

    private fun assertTokenFormStayed(activity: MainActivity, form: APITokenView, history: History) {
        finishTransition()
        assertSame(form, activity.binding.containerView.children.single())
        assertEquals(history.toList(), Flow.get(activity).history.toList())
        assertFalse(activity.isFinishing)
    }

    private fun finishTransition() {
        shadowOf(Looper.getMainLooper()).idleFor(ScreenTransition.DURATION_MILLIS * 2, TimeUnit.MILLISECONDS)
    }
}
