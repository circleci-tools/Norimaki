package com.unhappychoice.norimaki.presentation.presenter

import com.github.unhappychoice.circleci.CircleCIAPIClientV1_1
import com.unhappychoice.norimaki.domain.model.ProjectId
import com.unhappychoice.norimaki.domain.service.SessionService
import com.unhappychoice.norimaki.infrastructure.flakytests.FlakyTestSummary
import com.unhappychoice.norimaki.infrastructure.flakytests.FlakyTestsClient
import com.unhappychoice.norimaki.presentation.view.FlakyTestsView
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifyOrder
import io.reactivex.Observable
import io.reactivex.android.plugins.RxAndroidPlugins
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.schedulers.Schedulers
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.factory
import org.kodein.di.singleton
import java.io.IOException

class FlakyTestsPresenterTest : DescribeSpec({
    it("loads flaky tests for the project and retries after a request failure") {
        RxJavaPlugins.setIoSchedulerHandler { Schedulers.trampoline() }
        RxAndroidPlugins.setInitMainThreadSchedulerHandler { Schedulers.trampoline() }
        RxAndroidPlugins.setMainThreadSchedulerHandler { Schedulers.trampoline() }

        val projectId = ProjectId("github", "owner", "repo")
        val results = FlakyTestSummary("testA", "ExampleTest", 3)
        val client = mockk<FlakyTestsClient> {
            every { fetch(projectId) } returnsMany listOf(
                Observable.error(IOException()),
                Observable.just(listOf(results))
            )
        }
        val presenter = FlakyTestsPresenter().apply {
            di = DI {
                bind<ProjectId>() with singleton { projectId }
                bind<SessionService>() with singleton { testSession(mockk<CircleCIAPIClientV1_1>()) }
                bind<FlakyTestsClient>() with factory { token: String ->
                    token shouldBe "test-token"
                    client
                }
            }
        }
        val view = mockk<FlakyTestsView>(relaxUnitFun = true)
        presenter.render()
        val scope = presenter.attachToTestScope(view)
        try {
            presenter.render()
            presenter.load()

            verifyOrder {
                client.fetch(projectId)
                view.render(FlakyTestsState.Failed)
                client.fetch(projectId)
                view.render(FlakyTestsState.Loaded(listOf(results)))
            }
        } finally {
            scope.destroy()
            RxAndroidPlugins.reset()
            RxJavaPlugins.reset()
        }
    }
})
