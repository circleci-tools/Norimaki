package com.unhappychoice.norimaki

import com.github.unhappychoice.circleci.CircleCIAPIClientV1_1
import com.github.unhappychoice.circleci.v1.response.Project
import com.github.unhappychoice.circleci.v1.response.User
import com.unhappychoice.norimaki.domain.service.SessionService
import io.mockk.every
import io.mockk.mockk
import io.reactivex.Observable
import io.reactivex.Scheduler
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.SingleSubject
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.singleton

class BackTestApplication : NorimakiApplication() {
    var credentialScheduler: Scheduler = Schedulers.trampoline()
    var sessionObserverScheduler: Scheduler = Schedulers.trampoline()
    var beforeReadToken = {}
    var beforeWriteToken = {}
    var savedToken = "saved"
        private set

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
        return SessionService(
            readToken = {
                beforeReadToken()
                savedToken
            },
            writeToken = {
                beforeWriteToken()
                savedToken = it
            },
            createClient = clients::getValue,
            verify = { client ->
                val token = clients.entries.single { it.value === client }.key
                verifiedTokens.add(token)
                if (token == "rejected") rejection else Single.just(user)
            },
            ioScheduler = credentialScheduler,
            observerScheduler = sessionObserverScheduler
        )
    }
}
