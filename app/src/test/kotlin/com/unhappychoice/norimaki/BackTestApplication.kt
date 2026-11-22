package com.unhappychoice.norimaki

import com.github.unhappychoice.circleci.CircleCIAPIClientV1_1
import com.github.unhappychoice.circleci.v1.response.Project
import com.github.unhappychoice.circleci.v1.response.User
import com.unhappychoice.norimaki.domain.service.SessionService
import io.mockk.every
import io.mockk.mockk
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.subjects.SingleSubject
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.singleton

class BackTestApplication : NorimakiApplication() {
    var projects: Observable<List<Project>> = Observable.never()
    val projectBuildRequests = mutableListOf<Triple<String, String, String>>()
    val verifiedTokens = mutableListOf<String>()
    val rejection: SingleSubject<User> = SingleSubject.create()

    override val module by lazy {
        DI {
            extend(super.module, allowOverride = true)
            bind<SessionService>(overrides = true) with singleton { createSession() }
        }
    }

    private fun createSession(): SessionService {
        var savedToken = "saved"
        val user = mockk<User>(relaxed = true)
        val clients = listOf("saved", "rejected", "replacement").associateWith {
            mockk<CircleCIAPIClientV1_1> {
                every { getProjects() } answers { projects }
                every { getRecentBuilds(any(), any()) } returns Observable.never()
                every { getProjectBuilds(any(), any(), any(), any(), any()) } answers {
                    projectBuildRequests.add(Triple(firstArg(), secondArg(), thirdArg()))
                    Observable.never()
                }
            }
        }
        return SessionService({ savedToken }, { savedToken = it }, clients::getValue) { client ->
            val token = clients.entries.single { it.value === client }.key
            verifiedTokens.add(token)
            if (token == "rejected") rejection else Single.just(user)
        }
    }
}
