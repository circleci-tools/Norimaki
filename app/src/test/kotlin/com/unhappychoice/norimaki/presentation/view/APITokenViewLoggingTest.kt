package com.unhappychoice.norimaki.presentation.view

import android.app.Activity
import android.app.Application
import androidx.lifecycle.MutableLiveData
import com.unhappychoice.norimaki.databinding.ApiTokenViewBinding
import com.unhappychoice.norimaki.presentation.presenter.APITokenPresenter
import com.unhappychoice.norimaki.presentation.viewmodel.APITokenViewModel
import com.unhappychoice.norimaki.presentation.viewmodel.TokenVerification
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.singleton
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class APITokenViewLoggingTest {
    @Test
    fun tokenIsBoundAndAppliedWithoutLoggingIt() {
        val storedToken = "dummy-stored-token"
        val enteredToken = "dummy-entered-token"
        val editedToken = "dummy-edited-token"
        val model = mockk<APITokenViewModel>(relaxed = true) {
            every { token } returns storedToken
            every { verification } returns MutableLiveData(TokenVerification.IDLE)
        }
        val presenter = mockk<APITokenPresenter>(relaxed = true) {
            every { this@mockk.model } returns model
        }

        Robolectric.buildActivity(Activity::class.java).setup().use { controller ->
            val activity = controller.get()
            val form = showForm(activity, presenter)
            val input = form.accessTokenTextView
            assertEquals(storedToken, input.text.toString())

            input.setText(enteredToken)
            input.setText(editedToken)
            form.submitButton.performClick()
            verify(exactly = 1) { model.submit(editedToken) }

            every { model.token } returns editedToken
            val reopened = showForm(activity, presenter)
            assertEquals(editedToken, reopened.accessTokenTextView.text.toString())

            val dummyTokens = listOf(storedToken, enteredToken, editedToken)
            val tokenWasLogged = ShadowLog.getLogs().any { entry -> dummyTokens.any { it in entry.msg.orEmpty() } }
            assertFalse("Token text appeared in the Android log", tokenWasLogged)
        }
    }

    private fun showForm(activity: Activity, presenter: APITokenPresenter): ApiTokenViewBinding =
        ApiTokenViewBinding.inflate(activity.layoutInflater).apply {
            root.di = DI { bind<APITokenPresenter>() with singleton { presenter } }
            activity.setContentView(root)
        }
}
