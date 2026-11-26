package com.unhappychoice.norimaki.presentation.core

import com.github.unhappychoice.circleci.CircleCIAPIClientV1_1
import com.github.unhappychoice.circleci.v1.response.User
import com.unhappychoice.norimaki.domain.service.SessionService
import com.unhappychoice.norimaki.presentation.screen.APITokenScreen
import com.unhappychoice.norimaki.presentation.screen.BuildListScreen
import flow.Flow
import flow.History
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.reactivex.Single
import io.reactivex.subjects.SingleSubject

class BackNavigationTest : DescribeSpec({
    describe("Back from the token screen") {
        it("returns to the previous screen when the session is still active") {
            val fixture = BackNavigationFixture()
            fixture.session.restore().blockingGet()

            fixture.flow.goBackFromScreen(fixture.session, true) shouldBe true

            verify(exactly = 1) { fixture.flow.goBack() }
            fixture.verifiedTokens shouldBe listOf("saved")
        }

        it("keeps the form open during verification and after rejection without restoring the saved token") {
            val fixture = BackNavigationFixture()
            fixture.session.restore().blockingGet()
            val replacement = fixture.session.replace("rejected").test()

            fixture.flow.goBackFromScreen(fixture.session, true) shouldBe true
            fixture.rejection.onError(IllegalArgumentException("Rejected token"))
            replacement.assertError(IllegalArgumentException::class.java)
            fixture.flow.goBackFromScreen(fixture.session, true) shouldBe true

            verify(exactly = 0) { fixture.flow.goBack() }
            fixture.verifiedTokens shouldBe listOf("saved", "rejected")
            fixture.session.savedToken shouldBe "saved"

            val verified = fixture.session.replace("new").blockingGet()
            fixture.session.restore().blockingGet() shouldBe verified
            fixture.session.savedToken shouldBe "new"
            fixture.verifiedTokens shouldBe listOf("saved", "rejected", "new")
        }

        it("allows default Back when the token form is the only screen") {
            val fixture = BackNavigationFixture()
            every { fixture.flow.history } returns History.single(APITokenScreen())
            every { fixture.flow.goBack() } returns false

            fixture.flow.goBackFromScreen(fixture.session, true) shouldBe false
        }
    }
})

private class BackNavigationFixture {
    private var savedToken = "saved"
    private val clients = listOf("saved", "rejected", "new")
        .associateWith { mockk<CircleCIAPIClientV1_1>() }
    val verifiedTokens = mutableListOf<String>()
    val rejection: SingleSubject<User> = SingleSubject.create()
    val session = SessionService(
        readToken = { savedToken },
        writeToken = { savedToken = it },
        createClient = clients::getValue,
        verify = { client ->
            val token = clients.entries.single { it.value === client }.key
            verifiedTokens.add(token)
            if (token == "rejected") rejection else Single.just(mockk<User>())
        }
    )
    val flow = mockk<Flow> {
        every { history } returns History.single(BuildListScreen(null)).buildUpon().push(APITokenScreen()).build()
        every { goBack() } answers {
            session.restore().blockingGet()
            true
        }
    }
}
