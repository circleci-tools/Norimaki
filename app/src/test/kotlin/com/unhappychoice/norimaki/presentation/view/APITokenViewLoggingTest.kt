package com.unhappychoice.norimaki.presentation.view

import android.app.Activity
import android.app.Application
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.lifecycle.Observer
import com.github.unhappychoice.circleci.v1.response.User
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.databinding.ApiTokenViewBinding
import com.unhappychoice.norimaki.domain.service.SessionService
import com.unhappychoice.norimaki.infrastructure.preference.APITokenPreference
import com.unhappychoice.norimaki.presentation.presenter.APITokenPresenter
import com.unhappychoice.norimaki.presentation.viewmodel.APITokenViewModel
import com.unhappychoice.norimaki.presentation.viewmodel.TokenVerification
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.reactivex.subjects.SingleSubject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.singleton
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import javax.crypto.KeyGenerator
import javax.crypto.spec.SecretKeySpec

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class APITokenViewLoggingTest {
    @Test
    fun tokenIsBoundAndAppliedWithoutLoggingIt() {
        val storedToken = "dummy-stored-token"
        val enteredToken = "dummy-entered-token"
        val editedToken = "dummy-edited-token"
        var savedToken = storedToken
        val verification = SingleSubject.create<User>()
        val model = APITokenViewModel(SessionService(
            readToken = { savedToken },
            writeToken = { savedToken = it },
            createClient = { mockk() },
            verify = { verification }
        ))
        Robolectric.buildActivity(ComponentActivity::class.java).setup().use { controller ->
            val activity = controller.get().apply { setTheme(R.style.AppTheme) }
            val presenter = presenterFor(activity, model)
            val form = showForm(activity, presenter)
            val input = form.accessTokenTextView
            assertEquals(storedToken, input.text.toString())

            input.setText(enteredToken)
            assertEquals(enteredToken, input.text.toString())
            assertEquals(storedToken, savedToken)
            input.setText(editedToken)
            assertEquals(editedToken, input.text.toString())
            assertEquals(storedToken, savedToken)
            form.submitButton.performClick()
            assertEquals(storedToken, savedToken)
            verify(exactly = 0) { presenter.goToBuildList() }

            val reopened = showForm(activity, presenter)
            assertEquals(editedToken, reopened.accessTokenTextView.text.toString())

            verification.onSuccess(mockk())
            assertEquals(editedToken, savedToken)
            verify(exactly = 1) { presenter.goToBuildList() }

            val dummyTokens = listOf(storedToken, enteredToken, editedToken)
            val tokenWasLogged = ShadowLog.getLogs().any { entry -> dummyTokens.any { it in entry.msg.orEmpty() } }
            assertFalse("Token text appeared in the Android log", tokenWasLogged)
        }
    }

    @Test
    fun showsSaveErrorAndStaysOnFormWhenEncryptionFailsAfterVerification() {
        Robolectric.buildActivity(ComponentActivity::class.java).setup().use { controller ->
            val activity = controller.get().apply { setTheme(R.style.AppTheme) }
            val preferences = activity.getSharedPreferences("token-replacement", Context.MODE_PRIVATE)
            val key = KeyGenerator.getInstance("AES").generateKey()
            val readable = APITokenPreference(preferences, key).apply { token = "saved-token" }
            val unwritable = APITokenPreference(preferences, SecretKeySpec(ByteArray(5), "AES"))
            var verification = SingleSubject.create<User>()
            var destination = unwritable
            val session = SessionService(
                readToken = { readable.token },
                writeToken = { destination.token = it },
                createClient = { mockk() },
                verify = { verification }
            )
            val model = APITokenViewModel(session)
            val presenter = presenterFor(activity, model)
            val form = showForm(activity, presenter)

            form.accessTokenTextView.setText("replacement-token")
            form.submitButton.performClick()

            assertFalse(form.submitButton.isEnabled)
            verification.onSuccess(mockk())

            assertEquals(TokenVerification.SAVE_FAILED, model.verification.value)
            assertEquals(activity.getString(R.string.api_token_save_failed), form.accessTokenTextView.error.toString())
            assertEquals("replacement-token", form.accessTokenTextView.text.toString())
            assertTrue(form.submitButton.isEnabled)
            assertTrue(form.accessTokenTextView.isEnabled)
            assertFalse(session.hasVerifiedSession)
            assertEquals("saved-token", readable.token)
            verify(exactly = 0) { presenter.goToBuildList() }

            val reopened = showForm(activity, presenter)
            assertEquals("replacement-token", reopened.accessTokenTextView.text.toString())
            assertTrue(reopened.submitButton.isEnabled)

            destination = readable
            verification = SingleSubject.create()
            reopened.submitButton.performClick()
            verification.onSuccess(mockk())

            assertTrue(session.hasVerifiedSession)
            assertEquals("replacement-token", readable.token)
            verify(exactly = 1) { presenter.goToBuildList() }
        }
    }

    private fun presenterFor(activity: ComponentActivity, model: APITokenViewModel): APITokenPresenter =
        mockk<APITokenPresenter>(relaxed = true) {
            every { this@mockk.model } returns model
            every { observeVerification(any()) } answers {
                model.verification.observe(activity, firstArg<Observer<TokenVerification>>())
            }
            every { goToBuildList() } answers { model.reset() }
        }

    private fun showForm(activity: Activity, presenter: APITokenPresenter): ApiTokenViewBinding =
        ApiTokenViewBinding.inflate(activity.layoutInflater).apply {
            root.di = DI { bind<APITokenPresenter>() with singleton { presenter } }
            activity.setContentView(root)
        }
}
