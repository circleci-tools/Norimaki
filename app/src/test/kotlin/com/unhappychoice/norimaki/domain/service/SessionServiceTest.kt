package com.unhappychoice.norimaki.domain.service

import com.github.unhappychoice.circleci.CircleCIAPIClientV1_1
import com.github.unhappychoice.circleci.v1.response.User
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.reactivex.Scheduler
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import io.reactivex.schedulers.TestScheduler
import io.reactivex.subjects.SingleSubject

class SessionServiceTest : DescribeSpec({
    describe("SessionService") {
        it("restores a saved token once and associates the verified user with its client") {
            val fixture = SessionFixture()
            val accounts = fixture.session.changes.test()
            val restored = fixture.session.restore().blockingGet()

            fixture.session.restore().blockingGet() shouldBe restored
            accounts.values().map { it.toNullable() }.filterNotNull() shouldBe listOf(restored)
            fixture.writtenTokens shouldBe emptyList<String>()
            fixture.verifiedClients shouldBe listOf(fixture.firstClient)
            restored.token shouldBe "first"
            restored.user shouldBe fixture.firstUser
            restored.client shouldBe fixture.firstClient
        }

        it("shares an in-flight restore across screens even when the first screen leaves") {
            val verification = SingleSubject.create<User>()
            val fixture = SessionFixture(verification)
            val firstScreen = fixture.session.restore().test()
            val secondScreen = fixture.session.restore().test()
            firstScreen.dispose()
            val thirdScreen = fixture.session.restore().test()

            fixture.verifiedClients shouldBe listOf(fixture.firstClient)
            verification.onSuccess(fixture.firstUser)

            val restored = fixture.session.restore().blockingGet()
            secondScreen.assertResult(restored)
            thirdScreen.assertResult(restored)
            fixture.verifiedClients shouldBe listOf(fixture.firstClient)
        }

        it("shares a restore failure and verifies again on retry") {
            val verification = SingleSubject.create<User>()
            val fixture = SessionFixture(verification)
            val firstScreen = fixture.session.restore().test()
            val secondScreen = fixture.session.restore().test()
            val failure = IllegalStateException("Rejected token")

            verification.onError(failure)

            firstScreen.assertError(failure)
            secondScreen.assertError(failure)
            fixture.verifiedClients shouldBe listOf(fixture.firstClient)
            fixture.session.restore().test().assertError(failure)
            fixture.verifiedClients shouldBe listOf(fixture.firstClient, fixture.firstClient)
            fixture.writtenTokens shouldBe emptyList<String>()
        }

        it("clears the first account while verifying and publishes the second account on success") {
            val fixture = SessionFixture()
            val first = fixture.session.restore().blockingGet()
            val accounts = fixture.session.changes.test()
            val writesAtPublication = fixture.session.changes
                .filter { it.toNullable()?.token == "second" }
                .map { fixture.writtenTokens.toList() }
                .test()
            val replacement = fixture.session.replace("second").test()

            accounts.values().map { it.toNullable() } shouldBe listOf(first, null)
            fixture.savedToken shouldBe "first"
            fixture.writtenTokens shouldBe emptyList<String>()

            fixture.secondVerification.onSuccess(fixture.secondUser)

            replacement.assertComplete()
            val second = replacement.values().single()
            accounts.values().map { it.toNullable() } shouldBe listOf(first, null, second)
            fixture.savedToken shouldBe "second"
            fixture.writtenTokens shouldBe listOf("second")
            writesAtPublication.assertValue(listOf("second"))
            second.user shouldBe fixture.secondUser
            second.client shouldBe fixture.secondClient
        }

        it("leaves the session empty after an invalid replacement and allows a retry") {
            val fixture = SessionFixture()
            val first = fixture.session.restore().blockingGet()
            val accounts = fixture.session.changes.test()
            val replacement = fixture.session.replace("second").test()
            val failure = IllegalStateException("Rejected token")

            fixture.secondVerification.onError(failure)

            replacement.assertError(failure)
            accounts.values().map { it.toNullable() } shouldBe listOf(first, null)
            fixture.savedToken shouldBe "first"
            fixture.writtenTokens shouldBe emptyList<String>()
            fixture.session.replace("first").test().assertComplete()
            fixture.verifiedClients shouldBe listOf(fixture.firstClient, fixture.secondClient, fixture.firstClient)
        }

        it("does not publish a replacement when saving the token fails") {
            val failure = IllegalStateException("Credential store unavailable")
            val fixture = SessionFixture(writeFailure = failure)
            fixture.session.restore().blockingGet()

            val replacement = fixture.session.replace("second").test()
            fixture.secondVerification.onSuccess(fixture.secondUser)

            replacement.assertError(failure)
            fixture.session.hasVerifiedSession shouldBe false
            fixture.savedToken shouldBe "first"
        }

        it("skips an older replacement that is still queued when a newer one is requested") {
            val io = TestScheduler()
            val fixture = SessionFixture(ioScheduler = io)
            val earlier = fixture.session.replace("second").test()
            val latest = fixture.session.replace("first").test()

            io.triggerActions()

            earlier.assertError(IllegalStateException::class.java)
            latest.assertComplete()
            fixture.verifiedClients shouldBe listOf(fixture.firstClient)
            fixture.writtenTokens shouldBe listOf("first")
        }

        it("keeps a superseded verification from activating its account") {
            val fixture = SessionFixture()
            val pending = fixture.session.replace("second").test()
            val latest = fixture.session.replace("first").blockingGet()

            fixture.secondVerification.onSuccess(fixture.secondUser)

            pending.assertError(IllegalStateException::class.java)
            fixture.session.restore().blockingGet() shouldBe latest
            fixture.savedToken shouldBe "first"
        }

        it("rejects an earlier verification that completes before a queued replacement starts") {
            val io = TestScheduler()
            val fixture = SessionFixture(ioScheduler = io)
            val accounts = fixture.session.changes.test()
            val earlier = fixture.session.replace("second").test()
            io.triggerActions()

            fixture.secondVerification.onSuccess(fixture.secondUser)
            val latest = fixture.session.replace("first").test()
            io.triggerActions()

            earlier.assertError(IllegalStateException::class.java)
            latest.assertComplete()
            fixture.writtenTokens shouldBe listOf("first")
            accounts.values().mapNotNull { it.toNullable()?.token } shouldBe listOf("first")
        }

        it("rejects a restore that completes before a queued replacement starts") {
            val io = TestScheduler()
            val verification = SingleSubject.create<User>()
            val fixture = SessionFixture(verification, ioScheduler = io)
            val accounts = fixture.session.changes.test()
            val restore = fixture.session.restore().test()
            io.triggerActions()

            verification.onSuccess(fixture.firstUser)
            val replacement = fixture.session.replace("second").test()
            io.triggerActions()
            fixture.secondVerification.onSuccess(fixture.secondUser)
            io.triggerActions()

            restore.assertError(IllegalStateException::class.java)
            replacement.assertComplete()
            fixture.writtenTokens shouldBe listOf("second")
            accounts.values().mapNotNull { it.toNullable()?.token } shouldBe listOf("second")
        }
    }
})

private class SessionFixture(
    firstVerification: Single<User>? = null,
    writeFailure: Throwable? = null,
    ioScheduler: Scheduler = Schedulers.trampoline()
) {
    var savedToken = "first"
    val writtenTokens = mutableListOf<String>()
    val firstClient = mockk<CircleCIAPIClientV1_1>()
    val secondClient = mockk<CircleCIAPIClientV1_1>()
    val firstUser = mockk<User>()
    val secondUser = mockk<User>()
    val verifiedClients = mutableListOf<CircleCIAPIClientV1_1>()
    val secondVerification: SingleSubject<User> = SingleSubject.create()

    val session = SessionService(
        readToken = { savedToken },
        writeToken = {
            writeFailure?.let { throw it }
            writtenTokens.add(it)
            savedToken = it
        },
        createClient = { if (it == "first") firstClient else secondClient },
        verify = {
            verifiedClients.add(it)
            if (it === firstClient) firstVerification ?: Single.just(firstUser) else secondVerification
        },
        ioScheduler = ioScheduler
    )
}
