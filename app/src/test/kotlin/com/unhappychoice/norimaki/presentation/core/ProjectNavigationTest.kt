package com.unhappychoice.norimaki.presentation.core

import com.github.unhappychoice.circleci.v1.response.Project
import com.gojuno.koptional.None
import com.gojuno.koptional.Some
import com.unhappychoice.norimaki.domain.service.EventBusService
import com.unhappychoice.norimaki.presentation.screen.BuildListScreen
import flow.Direction
import flow.Flow
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.reactivex.disposables.Disposable

class ProjectNavigationTest : DescribeSpec({
    describe("Flow.subscribeToProjectNavigation()") {
        val eventBus = EventBusService()
        val flow = mockk<Flow>(relaxed = true)
        lateinit var subscription: Disposable

        beforeEach {
            clearMocks(flow)
            subscription = flow.subscribeToProjectNavigation(eventBus)
        }

        afterEach { subscription.dispose() }

        it("opens Recent Builds once without a build list presenter") {
            eventBus.selectProject.onNext(None)

            verify(exactly = 1) { flow.replaceTop(BuildListScreen(""), Direction.REPLACE) }
        }

        it("opens the selected project once without a build list presenter") {
            val project = mockk<Project> {
                every { vcsUrl } returns "https://bitbucket.org/team/repo"
                every { username } returns "team"
                every { reponame } returns "repo"
            }

            eventBus.selectProject.onNext(Some(project))

            verify(exactly = 1) {
                flow.replaceTop(BuildListScreen("bitbucket/team/repo"), Direction.REPLACE)
            }
        }

        it("stops handling selections when the subscription is disposed") {
            subscription.dispose()
            eventBus.selectProject.onNext(None)

            verify(exactly = 0) { flow.replaceTop(any(), any()) }
        }
    }
})
