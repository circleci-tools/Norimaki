package com.unhappychoice.norimaki.presentation.viewmodel

import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import androidx.lifecycle.Observer
import com.github.unhappychoice.circleci.v1.response.User
import com.unhappychoice.norimaki.domain.service.SessionService
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.reactivex.subjects.SingleSubject

class APITokenViewModelTest : DescribeSpec({
    beforeSpec {
        ArchTaskExecutor.getInstance().setDelegate(object : TaskExecutor() {
            override fun executeOnDiskIO(runnable: Runnable) = runnable.run()
            override fun postToMainThread(runnable: Runnable) = runnable.run()
            override fun isMainThread() = true
        })
    }

    afterSpec {
        ArchTaskExecutor.getInstance().setDelegate(null)
    }

    it("delivers a verification that succeeds between form observers") {
        val fixture = VerificationFixture()
        val originalStates = mutableListOf<TokenVerification>()
        val original = Observer<TokenVerification> { originalStates.add(it) }
        fixture.model.verification.observeForever(original)

        fixture.model.submit("candidate")
        fixture.model.verification.removeObserver(original)
        fixture.pending.onSuccess(mockk())
        fixture.model.verification.observeForever { fixture.states.add(it) }

        originalStates shouldBe listOf(TokenVerification.IDLE, TokenVerification.VERIFYING)
        fixture.states shouldBe listOf(TokenVerification.VERIFIED)
        fixture.savedToken shouldBe "candidate"
        fixture.verifications shouldBe 1
    }

    it("delivers a rejection to the recreated form and accepts a retry") {
        val fixture = VerificationFixture()
        val original = Observer<TokenVerification> {}
        fixture.model.verification.observeForever(original)
        fixture.model.submit("candidate")
        fixture.model.verification.removeObserver(original)
        fixture.model.verification.observeForever { fixture.states.add(it) }

        fixture.pending.onError(IllegalStateException("Rejected token"))

        fixture.model.token().blockingGet() shouldBe "candidate"
        fixture.savedToken shouldBe "saved"
        fixture.pending = SingleSubject.create()
        fixture.model.submit("retry")
        fixture.pending.onSuccess(mockk())

        fixture.states shouldBe listOf(
            TokenVerification.VERIFYING, TokenVerification.REJECTED,
            TokenVerification.VERIFYING, TokenVerification.VERIFIED
        )
        fixture.savedToken shouldBe "retry"
    }
})

private class VerificationFixture {
    var savedToken = "saved"
    var verifications = 0
    var pending = SingleSubject.create<User>()
    val states = mutableListOf<TokenVerification>()

    val model = APITokenViewModel(
        SessionService(
            readToken = { savedToken },
            writeToken = { savedToken = it },
            createClient = { mockk() },
            verify = {
                verifications++
                pending
            }
        )
    )
}
