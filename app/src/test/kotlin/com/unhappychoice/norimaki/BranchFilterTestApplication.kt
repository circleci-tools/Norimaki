package com.unhappychoice.norimaki

import com.github.unhappychoice.circleci.CircleCIAPIClientV1_1
import com.unhappychoice.norimaki.domain.service.SessionService
import com.unhappychoice.norimaki.presentation.presenter.testSession
import io.mockk.every
import io.mockk.mockk
import io.reactivex.Observable
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.singleton

class BranchFilterTestApplication : NorimakiApplication() {
    val api = mockk<CircleCIAPIClientV1_1> {
        every { getProjectBuilds(any(), any(), any(), any(), any()) } returns Observable.just(emptyList())
        every { getBranchBuilds(any(), any(), any(), any(), any(), any()) } returns Observable.just(emptyList())
        every { getBuild(any(), any(), any(), any()) } returns Observable.never()
    }

    override val module by lazy {
        DI {
            extend(super.module, allowOverride = true)
            bind<SessionService>(overrides = true) with singleton { testSession(api) }
        }
    }
}
