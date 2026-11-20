package com.unhappychoice.norimaki.presentation.presenter

import com.github.unhappychoice.circleci.CircleCIAPIClientV1_1
import com.github.unhappychoice.circleci.v1.response.Build
import com.github.unhappychoice.circleci.v1.response.BuildAction
import com.github.unhappychoice.circleci.v1.response.BuildStep
import com.unhappychoice.norimaki.domain.model.BuildId
import com.unhappychoice.norimaki.domain.model.BuildStepId
import com.unhappychoice.norimaki.domain.service.SessionService
import com.unhappychoice.norimaki.infrastructure.output.BuildOutputClient
import com.unhappychoice.norimaki.presentation.view.BuildStepView
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.reactivex.Observable
import io.reactivex.android.plugins.RxAndroidPlugins
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.schedulers.Schedulers
import io.reactivex.schedulers.TestScheduler
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.singleton
import java.io.IOException
import java.util.Date
import java.util.concurrent.TimeUnit.SECONDS
import mortar.MortarScope

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
        val scenarios = listOf("selected step", "build", "final download failure", "failure without output", "split hyperlink")
        scenarios.forEach { scenario ->
            it("updates log search when polling ends with $scenario") {
                val scheduler = TestScheduler()
                val runningStep = BuildStep("Run tests", listOf(action(5, null)))
                val first = BuildStep("Run tests", listOf(action(2, Date())))
                val running = build("running", listOf(first, runningStep))
                val finishedStep = BuildStep("Run tests", listOf(action(5, Date())))
                val finished = if (scenario == "build") {
                    build("finished", listOf(first, runningStep))
                } else {
                    build("running", listOf(first, finishedStep))
                }
                val finalDownloadFails = scenario.contains("failure")
                val hasOutput = scenario != "failure without output"
                val liveChunks = when (scenario) {
                    "split hyperlink" -> listOf("before \u001B", "]8;id=hidden", ";https://", "example.test", "\u001B")
                    "failure without output" -> emptyList()
                    else -> listOf("0", "1", "2", "3", "4")
                }.map { it.toByteArray() }
                val chunksByOffset = liveChunks.runningFold(0L) { offset, chunk -> offset + chunk.size }
                    .zip(liveChunks).toMap()
                val finalOffset = liveChunks.sumOf { it.size.toLong() }
                val finalChunk = if (scenario == "split hyperlink") {
                    "\\Report\u001B]8;;\u0007"
                } else {
                    "done"
                }
                val api = mockk<CircleCIAPIClientV1_1> {
                    every { getBuild("github", "owner", "repo", 42) } returnsMany
                        listOf(Observable.just(running), Observable.just(running), Observable.just(finished))
                }
                val url = "https://circleci.com/api/private/output/raw/github/owner/repo/42/output/0/5"
                val output = mockk<BuildOutputClient> {
                    every { fetchRawOutput(url, any()) } answers {
                        Observable.just(chunksByOffset[secondArg<Long>()] ?: byteArrayOf())
                    }
                    every { fetchFinalRawOutput(url, finalOffset, any()) } returns if (finalDownloadFails) {
                        Observable.error(IOException("HTTP 503"))
                    } else {
                        Observable.just(finalChunk.toByteArray())
                    }
                }
                val presenter = BuildStepPresenter { output }.apply {
                    di = DI {
                        bind<BuildId>() with singleton { BuildId("github", "owner", "repo", 42) }
                        bind<BuildStepId>() with singleton { BuildStepId(5) }
                        bind<SessionService>() with singleton { testSession(api) }
                    }
                }
                val view = mockk<BuildStepView>(relaxUnitFun = true)
                var scope: MortarScope? = null

                try {
                    RxJavaPlugins.setComputationSchedulerHandler { scheduler }
                    RxJavaPlugins.setIoSchedulerHandler { Schedulers.trampoline() }
                    RxAndroidPlugins.setInitMainThreadSchedulerHandler { Schedulers.trampoline() }
                    RxAndroidPlugins.setMainThreadSchedulerHandler { Schedulers.trampoline() }

                    scope = presenter.attachToTestScope(view)

                    scheduler.advanceTimeBy(4, SECONDS)
                    verify(exactly = 1) { view.showStep(runningStep) }
                    val retainedLog = when (scenario) {
                        "split hyperlink" -> "before "
                        "failure without output" -> ""
                        else -> "01234"
                    }
                    presenter.logString.value shouldBe retainedLog
                    presenter.logSearchAvailable.value shouldBe false
                    presenter.logLoadFailed.value shouldBe false

                    scheduler.advanceTimeBy(1, SECONDS)
                    verify(exactly = 1) { output.fetchFinalRawOutput(url, finalOffset, any()) }
                    presenter.logLoadFailed.value shouldBe finalDownloadFails
                    val expectedFinalLog = when {
                        finalDownloadFails -> retainedLog
                        scenario == "split hyperlink" -> "before Report"
                        else -> retainedLog + "done"
                    }
                    presenter.logString.value shouldBe expectedFinalLog
                    presenter.logSearchAvailable.value shouldBe hasOutput
                } finally {
                    scope?.destroy()
                    RxAndroidPlugins.reset()
                    RxJavaPlugins.reset()
                }
            }
        }
    }

    listOf(false, true).forEach { outputFails ->
        it("reports the finished step output result when output fails: $outputFails") {
            val expectedUrl = "https://circleci.com/output"
            val finishedAction = action(5, Date()).apply {
                every { outputUrl } returns expectedUrl
            }
            val finishedStep = BuildStep("Run tests", listOf(finishedAction))
            val finishedBuild = build("finished", listOf(finishedStep))
            val api = mockk<CircleCIAPIClientV1_1> {
                every { getBuild("github", "owner", "repo", 42) } returns Observable.just(finishedBuild)
            }
            val output = mockk<BuildOutputClient> {
                every { fetchOutput(expectedUrl) } returns if (outputFails) {
                    Observable.error(IllegalStateException("Output load failed"))
                } else {
                    Observable.just("done")
                }
            }
            val presenter = BuildStepPresenter { output }.apply {
                di = DI {
                    bind<BuildId>() with singleton { BuildId("github", "owner", "repo", 42) }
                    bind<BuildStepId>() with singleton { BuildStepId(5) }
                    bind<SessionService>() with singleton { testSession(api) }
                }
            }
            val view = mockk<BuildStepView>(relaxUnitFun = true)
            var scope: MortarScope? = null

            try {
                RxJavaPlugins.setIoSchedulerHandler { Schedulers.trampoline() }
                RxAndroidPlugins.setInitMainThreadSchedulerHandler { Schedulers.trampoline() }
                RxAndroidPlugins.setMainThreadSchedulerHandler { Schedulers.trampoline() }

                scope = presenter.attachToTestScope(view)

                verify(exactly = 1) { view.showStep(finishedStep) }
                presenter.logLoadFailed.value shouldBe false
                presenter.logSearchAvailable.value shouldBe true
                verify(exactly = 1) { output.fetchOutput(expectedUrl) }
                presenter.logString.value shouldBe (if (outputFails) "<br>[Some output could not be loaded.]<br>" else "done")
            } finally {
                scope?.destroy()
                RxAndroidPlugins.reset()
                RxJavaPlugins.reset()
            }
        }
    }

    it("exposes the initial build load failure without retrying") {
        val scheduler = TestScheduler()
        val api = mockk<CircleCIAPIClientV1_1> {
            every { getBuild("github", "owner", "repo", 123) } returns
                Observable.error(IllegalStateException("Build load failed"))
        }
        val presenter = BuildStepPresenter().apply {
            di = DI {
                bind<BuildId>() with singleton { BuildId("github", "owner", "repo", 123) }
                bind<BuildStepId>() with singleton { BuildStepId(null, legacyName = "Run tests") }
                bind<SessionService>() with singleton { testSession(api) }
            }
        }
        val failures = presenter.logLoadFailed.asObservable().distinctUntilChanged().test()
        val view = mockk<BuildStepView>(relaxUnitFun = true)
        var scope: MortarScope? = null

        try {
            RxJavaPlugins.setComputationSchedulerHandler { scheduler }
            RxJavaPlugins.setIoSchedulerHandler { Schedulers.trampoline() }
            RxAndroidPlugins.setInitMainThreadSchedulerHandler { Schedulers.trampoline() }
            RxAndroidPlugins.setMainThreadSchedulerHandler { Schedulers.trampoline() }

            scope = presenter.attachToTestScope(view)

            failures.assertValues(false, true)
            verify(exactly = 0) { view.showStep(any()) }
            scheduler.advanceTimeBy(BuildPresenter.BUILD_POLLING_SECONDS * 3, SECONDS)
            verify(exactly = 1) { api.getBuild("github", "owner", "repo", 123) }
        } finally {
            scope?.destroy()
            failures.dispose()
            RxAndroidPlugins.reset()
            RxJavaPlugins.reset()
        }
    }
})
