package com.unhappychoice.norimaki.presentation.viewmodel

import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import androidx.lifecycle.Observer
import com.github.unhappychoice.circleci.v1.response.User
import com.unhappychoice.norimaki.domain.service.SessionService
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.reactivex.Scheduler
import io.reactivex.schedulers.Schedulers
import io.reactivex.schedulers.TestScheduler
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

    it("saves on the I/O scheduler and delivers success between form observers on the UI scheduler") {
        val persistence = TestScheduler()
        val ui = TestScheduler()
        val fixture = VerificationFixture(persistence, ui)
        val accounts = fixture.session.changes.test()
        val originalStates = mutableListOf<TokenVerification>()
        val original = Observer<TokenVerification> { originalStates.add(it) }
        fixture.model.verification.observeForever(original)

        fixture.model.submit("candidate")
        persistence.triggerActions()
        ui.triggerActions()
        fixture.model.verification.removeObserver(original)
        fixture.pending.onSuccess(mockk())
        fixture.savedToken shouldBe "saved"
        persistence.triggerActions()
        fixture.savedToken shouldBe "candidate"
        fixture.model.verification.value shouldBe TokenVerification.VERIFYING
        accounts.values().mapNotNull { it.toNullable()?.token } shouldBe emptyList<String>()

        ui.triggerActions()
        accounts.values().mapNotNull { it.toNullable()?.token } shouldBe listOf("candidate")
        fixture.model.verification.observeForever { fixture.states.add(it) }

        originalStates shouldBe listOf(TokenVerification.IDLE, TokenVerification.VERIFYING)
        fixture.states shouldBe listOf(TokenVerification.VERIFIED)
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

private class VerificationFixture(
    ioScheduler: Scheduler = Schedulers.trampoline(),
    observerScheduler: Scheduler = Schedulers.trampoline()
) {
    var savedToken = "saved"
    var verifications = 0
    var pending = SingleSubject.create<User>()
    val states = mutableListOf<TokenVerification>()

    val session = SessionService(
        readToken = { savedToken },
        writeToken = { savedToken = it },
        createClient = { mockk() },
        ioScheduler = ioScheduler,
        observerScheduler = observerScheduler,
        verify = {
            verifications++
            pending
        }
    )
    val model = APITokenViewModel(session)
}
