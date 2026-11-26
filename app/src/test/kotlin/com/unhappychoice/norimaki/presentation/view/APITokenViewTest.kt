package com.unhappychoice.norimaki.presentation.view

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Looper
import android.os.Parcelable
import android.text.InputType
import android.util.SparseArray
import android.view.View
import android.widget.Button
import android.widget.EditText
import com.unhappychoice.norimaki.MainActivity
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.infrastructure.preference.APITokenPreference
import com.unhappychoice.norimaki.presentation.screen.APITokenScreen
import com.unhappychoice.norimaki.presentation.screen.BuildListScreen
import flow.Flow
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.schedulers.TestScheduler
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class APITokenViewTest {
    @Before
    fun pauseNetworkSubscriptions() {
        val scheduler = TestScheduler()
        RxJavaPlugins.setIoSchedulerHandler { scheduler }
    }

    @After
    fun restoreSchedulers() {
        RxJavaPlugins.reset()
    }

    @Test
    fun masksTokensAndExcludesThemFromSavedViewState() {
        val preference = APITokenPreference(RuntimeEnvironment.getApplication())
        val savedToken = "saved-dummy-token"
        val replacement = "replacement_Dummy-Token.123"
        preference.token = savedToken

        Robolectric.buildActivity(MainActivity::class.java).use { controller ->
            val activity = controller.setup().get()
            val flow = Flow.get(activity)
            assertEquals(BuildListScreen(null), flow.history.top<Any>())
            flow.set(APITokenScreen())
            shadowOf(Looper.getMainLooper()).idle()
            val field = activity.findViewById<EditText>(R.id.accessTokenTextView)

            assertEquals(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD, field.inputType)
            assertMasked(field, savedToken)
            assertTokenStateAbsent(activity.window.decorView, field)

            val clipboard = requireNotNull(activity.getSystemService(ClipboardManager::class.java))
            clipboard.setPrimaryClip(ClipData.newPlainText("Dummy API token", replacement))
            field.requestFocus()
            field.selectAll()
            assertTrue(field.onTextContextMenuItem(android.R.id.paste))
            assertMasked(field, replacement)
            assertTokenStateAbsent(activity.window.decorView, field)

            val submit = activity.findViewById<Button>(R.id.submitButton)
            submit.performClick()

            assertFalse(submit.isEnabled)
            assertFalse(field.isEnabled)
            assertEquals(savedToken, preference.token)
        }
    }

    private fun assertTokenStateAbsent(root: View, field: EditText) {
        val state = SparseArray<Parcelable>()
        root.saveHierarchyState(state)
        assertFalse("The token field contributed saved view state", state.indexOfKey(field.id) >= 0)
    }

    private fun assertMasked(field: EditText, token: String) {
        assertEquals(token, field.text.toString())
        val transformation = requireNotNull(field.transformationMethod)
        val displayed = requireNotNull(transformation.getTransformation(field.text, field))
        assertEquals(List(token.length) { '•' }, displayed.toList())
    }
}
