package com.unhappychoice.norimaki.presentation.presenter

import com.github.unhappychoice.circleci.CircleCIAPIClientV1_1
import com.github.unhappychoice.circleci.v1.response.Build
import com.github.unhappychoice.circleci.v1.response.BuildAction
import com.github.unhappychoice.circleci.v1.response.BuildStep
import com.unhappychoice.norimaki.infrastructure.output.BuildOutputClient
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import io.reactivex.Observable
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.schedulers.TestScheduler
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.singleton
import java.util.Date
import java.util.concurrent.TimeUnit.SECONDS

class BuildStepPresenterTest : DescribeSpec({
    fun action(number: Int, end: Date?): BuildAction = mockk {
        every { index } returns 0
        every { step } returns number
        every { endTime } returns end
    }

    fun build(lifecycleStatus: String, buildSteps: List<BuildStep>): Build = mockk {
        every { lifecycle } returns lifecycleStatus
        every { steps } returns buildSteps
        every { vcsUrl } returns null
        every { username } returns "owner"
        every { reponame } returns "repo"
        every { buildNum } returns 42
    }

    describe("BuildStepPresenter running output") {
        listOf("selected step", "build").forEach { completion ->
            it("keeps fetching a same-named running step until the $completion finishes") {
                val scheduler = TestScheduler()
                val selected = action(5, null)
                val first = BuildStep("Run tests", listOf(action(2, Date())))
                val second = BuildStep("Run tests", listOf(selected))
                val running = build("running", listOf(first, second))
                val finished = if (completion == "build") {
                    build("finished", listOf(first, second))
                } else {
                    build("running", listOf(first, BuildStep("Run tests", listOf(action(5, Date())))))
                }
                val api = mockk<CircleCIAPIClientV1_1> {
                    every { getBuild("github", "owner", "repo", 42) } returnsMany
                        listOf(Observable.just(running), Observable.just(finished))
                }
                val output = mockk<BuildOutputClient> {
                    val url = "https://circleci.com/api/private/output/raw/github/owner/repo/42/output/0/5"
                    every { fetchRawOutput(url, any()) } answers {
                        Observable.just(('A' + secondArg<Long>().toInt()).toString().toByteArray())
                    }
                    every { fetchFinalRawOutput(url, 5L) } returns Observable.just("F".toByteArray())
                }
                val presenter = BuildStepPresenter().apply {
                    di = DI {
                        bind<Build>() with singleton { running }
                        bind<BuildStep>() with singleton { second }
                        bind<CircleCIAPIClientV1_1>() with singleton { api }
                        bind<BuildOutputClient>() with singleton { output }
                    }
                }

                val previousScheduler = RxJavaPlugins.getComputationSchedulerHandler()
                RxJavaPlugins.setComputationSchedulerHandler { scheduler }
                try {
                    val observer = presenter.runningOutput().test()
                    try {
                        scheduler.advanceTimeBy(4, SECONDS)
                        observer.assertNotComplete().assertNoErrors()
                        observer.assertValues("A", "B", "C", "D", "E")

                        scheduler.advanceTimeBy(1, SECONDS)
                        observer.assertComplete().assertNoErrors()
                        observer.assertValues("A", "B", "C", "D", "E", "F")
                    } finally {
                        observer.dispose()
                    }
                } finally {
                    RxJavaPlugins.setComputationSchedulerHandler(previousScheduler)
                }
            }
        }
    }
})
