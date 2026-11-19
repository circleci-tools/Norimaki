package com.unhappychoice.norimaki.presentation.presenter

import com.github.unhappychoice.circleci.CircleCIAPIClientV1_1
import com.github.unhappychoice.circleci.v1.response.Build
import com.github.unhappychoice.circleci.v1.response.BuildAction
import com.github.unhappychoice.circleci.v1.response.BuildStep
import com.unhappychoice.norimaki.infrastructure.output.BuildOutputClient
import com.unhappychoice.norimaki.infrastructure.output.RawOutputChunk
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.reactivex.Observable
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.schedulers.TestScheduler
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.singleton
import java.io.IOException
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

    describe("completedActionLogs()") {
        it("should keep earlier output, show a missing output notice, and continue") {
            completedActionLogs(listOf("first", "second", "third")) { url ->
                when (url) {
                    "second" -> Observable.error(IOException("HTTP 503"))
                    else -> Observable.just(url)
                }
            }.test().assertResult("first", "\n[Some output could not be loaded.]\n", "third")
        }

        it("should preserve action order when all downloads succeed") {
            completedActionLogs(listOf("first", "second")) { Observable.just(it) }
                .test().assertResult("first", "second")
        }

        it("should continue when creating a download throws") {
            completedActionLogs(listOf("invalid", "valid")) { url ->
                if (url == "invalid") throw IllegalArgumentException("Invalid URL")
                Observable.just(url)
            }.test().assertResult("\n[Some output could not be loaded.]\n", "valid")
        }
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
                        Observable.just(RawOutputChunk(byteArrayOf(secondArg<Long>().toByte())))
                    }
                    every { fetchFinalRawOutput(url, 5L) } returns Observable.just(RawOutputChunk(byteArrayOf(100)))
                }
                val presenter = BuildStepPresenter().apply {
                    di = DI {
                        bind<Build>() with singleton { running }
                        bind<BuildStep>() with singleton { second }
                    }
                }

                val previousScheduler = RxJavaPlugins.getComputationSchedulerHandler()
                RxJavaPlugins.setComputationSchedulerHandler { scheduler }
                try {
                    val observer = presenter.runningOutput(api, output).test()
                    try {
                        scheduler.advanceTimeBy(4, SECONDS)
                        observer.assertNotComplete().assertNoErrors()
                        observer.values().map { it.single().code } shouldBe listOf(0, 1, 2, 3, 4)

                        scheduler.advanceTimeBy(1, SECONDS)
                        observer.assertComplete().assertNoErrors()
                        observer.values().map { it.single().code } shouldBe listOf(0, 1, 2, 3, 4, 100)
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
