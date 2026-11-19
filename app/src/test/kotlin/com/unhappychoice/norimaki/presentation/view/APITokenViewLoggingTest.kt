package com.unhappychoice.norimaki.presentation.view

import android.app.Activity
import android.app.Application
import com.unhappychoice.norimaki.databinding.ApiTokenViewBinding
import com.unhappychoice.norimaki.domain.service.SessionService
import com.unhappychoice.norimaki.domain.service.VerifiedSession
import com.unhappychoice.norimaki.presentation.presenter.APITokenPresenter
import com.unhappychoice.norimaki.presentation.viewmodel.APITokenViewModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.reactivex.subjects.SingleSubject
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
        val verification = SingleSubject.create<VerifiedSession>()
        val session = mockk<SessionService> {
            every { savedToken } returns storedToken
            every { replace(editedToken) } returns verification
        }
        val viewModel = APITokenViewModel(session)
        val presenter = mockk<APITokenPresenter>(relaxed = true) {
            every { model } returns viewModel
            every { observeVerification(any()) } answers {
                viewModel.verification.observeForever(firstArg())
            }
        }

        Robolectric.buildActivity(Activity::class.java).setup().use { controller ->
            val activity = controller.get()
            val form = showForm(activity, presenter)
            val input = form.accessTokenTextView
            assertEquals(storedToken, input.text.toString())

            input.setText(enteredToken)
            input.setText(editedToken)
            form.submitButton.performClick()
            verify(exactly = 1) { session.replace(editedToken) }
            verify(exactly = 0) { presenter.goToBuildList() }

            val reopened = showForm(activity, presenter)
            assertEquals(editedToken, reopened.accessTokenTextView.text.toString())
            verification.onSuccess(mockk<VerifiedSession>())
            verify(exactly = 1) { presenter.goToBuildList() }

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
