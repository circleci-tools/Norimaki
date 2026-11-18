package com.unhappychoice.norimaki.presentation.view

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.text.InputType
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import com.unhappychoice.norimaki.MainActivity
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.infrastructure.preference.APITokenPreference
import com.unhappychoice.norimaki.presentation.presenter.APITokenPresenter
import flow.Flow
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import mortar.MortarScope
import mortar.bundler.BundleServiceRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.singleton

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class APITokenViewTest {
    @Test
    fun masksSavedAndPastedTokensAndSavesTheExactReplacement() {
        val application = RuntimeEnvironment.getApplication()
        val preference = APITokenPreference(application)
        val savedToken = "saved-dummy-token"
        val replacement = "replacement_Dummy-Token.123"
        preference.token = savedToken

        val presenterActivity = mockk<MainActivity> {
            every { applicationContext } returns application
            every { getSharedPreferences(any(), any()) } answers {
                application.getSharedPreferences(firstArg(), secondArg())
            }
        }
        val presenter = APITokenPresenter()
        val di = DI {
            bind<MainActivity>() with singleton { presenterActivity }
            bind<APITokenPresenter>() with singleton { presenter }
        }
        presenter.di = di

        val runner = BundleServiceRunner()
        val scope = MortarScope.buildRootScope()
            .withService(BundleServiceRunner.SERVICE_NAME, runner)
            .build("api_token_test")
        runner.onCreate(null)

        try {
            Robolectric.buildActivity(Activity::class.java).use { controller ->
                val activity = controller.setup().get()
                activity.setTheme(R.style.AppTheme)
                val view = LayoutInflater.from(activity).cloneInContext(scope.createContext(activity))
                    .inflate(R.layout.api_token_view, null) as APITokenView
                view.di = di
                activity.setContentView(view)
                val field = view.findViewById<EditText>(R.id.accessTokenTextView)

                assertEquals(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD, field.inputType)
                assertMasked(field, savedToken)

                val clipboard = requireNotNull(activity.getSystemService(ClipboardManager::class.java))
                clipboard.setPrimaryClip(ClipData.newPlainText("Dummy API token", replacement))
                field.requestFocus()
                field.selectAll()
                assertTrue(field.onTextContextMenuItem(android.R.id.paste))
                assertMasked(field, replacement)

                mockkStatic(Flow::class) {
                    every { Flow.get(presenterActivity) } returns mockk(relaxed = true)
                    view.findViewById<Button>(R.id.submitButton).performClick()
                }
                assertEquals(replacement, preference.token)
            }
        } finally {
            scope.destroy()
        }
    }

    private fun assertMasked(field: EditText, token: String) {
        assertEquals(token, field.text.toString())
        val transformation = requireNotNull(field.transformationMethod)
        val displayed = requireNotNull(transformation.getTransformation(field.text, field))
        assertEquals(List(token.length) { '•' }, displayed.toList())
    }
}
