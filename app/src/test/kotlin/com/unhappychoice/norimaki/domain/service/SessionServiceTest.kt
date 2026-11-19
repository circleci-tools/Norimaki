package com.unhappychoice.norimaki.domain.service

import com.github.unhappychoice.circleci.CircleCIAPIClientV1_1
import com.github.unhappychoice.circleci.v1.response.User
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.reactivex.Single
import io.reactivex.subjects.SingleSubject

class SessionServiceTest : DescribeSpec({
    describe("SessionService") {
        it("restores a saved token once and associates the verified user with its client") {
            val fixture = SessionFixture()
            val restored = fixture.session.restore().blockingGet()

            fixture.session.restore().blockingGet() shouldBe restored
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
        }

        it("clears the first account while verifying and publishes the second account on success") {
            val fixture = SessionFixture()
            val first = fixture.session.restore().blockingGet()
            val accounts = fixture.session.changes.test()
            val replacement = fixture.session.replace("second").test()

            accounts.values().map { it.toNullable() } shouldBe listOf(first, null)
            fixture.savedToken shouldBe "first"

            fixture.secondVerification.onSuccess(fixture.secondUser)

            replacement.assertComplete()
            val second = replacement.values().single()
            accounts.values().map { it.toNullable() } shouldBe listOf(first, null, second)
            fixture.savedToken shouldBe "second"
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
            fixture.session.replace("first").test().assertComplete()
            fixture.verifiedClients shouldBe listOf(fixture.firstClient, fixture.secondClient, fixture.firstClient)
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
    }
})

private class SessionFixture(firstVerification: Single<User>? = null) {
    var savedToken = "first"
    val firstClient = mockk<CircleCIAPIClientV1_1>()
    val secondClient = mockk<CircleCIAPIClientV1_1>()
    val firstUser = mockk<User>()
    val secondUser = mockk<User>()
    val verifiedClients = mutableListOf<CircleCIAPIClientV1_1>()
    val secondVerification: SingleSubject<User> = SingleSubject.create()

    val session = SessionService(
        readToken = { savedToken },
        writeToken = { savedToken = it },
        createClient = { if (it == "first") firstClient else secondClient },
        verify = {
            verifiedClients.add(it)
            if (it === firstClient) firstVerification ?: Single.just(firstUser) else secondVerification
        }
    )
}
