package com.unhappychoice.norimaki.presentation.presenter

import com.github.unhappychoice.circleci.CircleCIAPIClientV1_1
import com.github.unhappychoice.circleci.v1.response.Build
import com.github.unhappychoice.circleci.v1.response.BuildAction
import com.github.unhappychoice.circleci.v1.response.BuildStep
import com.unhappychoice.norimaki.domain.model.BuildId
import com.unhappychoice.norimaki.domain.model.BuildStepId
import com.unhappychoice.norimaki.domain.service.SessionService
import com.unhappychoice.norimaki.infrastructure.output.BuildOutputClient
import com.unhappychoice.norimaki.infrastructure.output.OUTPUT_LIMIT_MESSAGE
import com.unhappychoice.norimaki.infrastructure.output.RawOutputChunk
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
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.SECONDS
import mortar.MortarScope

class BuildStepPresenterTest : DescribeSpec({
    fun action(number: Int, end: Date?, actionIndex: Int = 0): BuildAction = mockk {
        every { index } returns actionIndex
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
        listOf("selected step", "build", "build with the selected step missing").forEach { completion ->
            it("keeps fetching a same-named running step until the $completion finishes") {
                val scheduler = TestScheduler()
                val selected = action(5, null)
                val first = BuildStep("Run tests", listOf(action(2, Date())))
                val second = BuildStep("Run tests", listOf(selected))
                val running = build("running", listOf(first, second))
                val runningSnapshot = if (completion == "build with the selected step missing") {
                    build("running", listOf(first))
                } else {
                    running
                }
                val finished = when (completion) {
                    "build" -> build("finished", listOf(first, second))
                    "build with the selected step missing" -> build("finished", listOf(first))
                    else -> build("running", listOf(first, BuildStep("Run tests", listOf(action(5, Date())))))
                }
                val api = mockk<CircleCIAPIClientV1_1> {
                    every { getBuild("github", "owner", "repo", 42) } returnsMany
                        listOf(Observable.just(runningSnapshot), Observable.just(finished))
                }
                val output = mockk<BuildOutputClient> {
                    val url = "https://circleci.com/api/private/output/raw/github/owner/repo/42/output/0/5"
                    every { fetchRawOutput(url, any()) } answers {
                        Observable.just(RawOutputChunk(byteArrayOf(secondArg<Long>().toByte())))
                    }
                    every { fetchFinalRawOutput(url, 10L) } returns Observable.just(RawOutputChunk(byteArrayOf(100)))
                }
                val presenter = BuildStepPresenter().apply {
                    di = DI {
                        bind<BuildId>() with singleton { BuildId("github", "owner", "repo", 42) }
                        bind<BuildStepId>() with singleton { BuildStepId(5) }
                    }
                }

                val previousScheduler = RxJavaPlugins.getComputationSchedulerHandler()
                RxJavaPlugins.setComputationSchedulerHandler { scheduler }
                try {
                    val observer = presenter.runningOutput(running, second, api, output).test()
                    try {
                        scheduler.advanceTimeBy(4, SECONDS)
                        observer.assertNotComplete().assertNoErrors()
                        observer.values().map { it.single().code } shouldBe listOf(0, 1, 2, 3, 4)

                        scheduler.advanceTimeTo(10, SECONDS)
                        observer.assertComplete().assertNoErrors()
                        observer.values().map { it.single().code } shouldBe (0..9).toList() + 100
                    } finally {
                        observer.dispose()
                    }
                } finally {
                    RxJavaPlugins.setComputationSchedulerHandler(previousScheduler)
                }
            }
        }
    }

    it("shows actions discovered during live polling and in the terminal snapshot without duplicates") {
        val scheduler = TestScheduler()
        val initialStep = BuildStep("Run tests", listOf(action(5, null)))
        val expandedStep = BuildStep("Run tests", listOf(action(5, null), action(5, null, 1)))
        val finishedStep = BuildStep("Run tests", (0..2).map { action(5, Date(), it) })
        val initial = build("running", listOf(initialStep))
        val expanded = build("running", listOf(expandedStep))
        val finished = build("running", listOf(finishedStep))
        val api = mockk<CircleCIAPIClientV1_1> {
            every { getBuild("github", "owner", "repo", 42) } returnsMany listOf(
                Observable.just(initial), Observable.just(expanded), Observable.just(finished)
            )
        }
        val urls = (0..2).map { "https://circleci.com/api/private/output/raw/github/owner/repo/42/output/$it/5" }
        val output = mockk<BuildOutputClient> {
            every { fetchRawOutput(urls[0], any()) } answers {
                Observable.just(RawOutputChunk("first".toByteArray().drop(secondArg<Long>().toInt()).toByteArray()))
            }
            every { fetchRawOutput(urls[1], any()) } answers {
                Observable.just(RawOutputChunk(" second".toByteArray().drop(secondArg<Long>().toInt()).toByteArray()))
            }
            every { fetchFinalRawOutput(urls[0], 5L, any()) } returns Observable.just(RawOutputChunk(" done".toByteArray()))
            every { fetchFinalRawOutput(urls[1], 7L, any()) } returns Observable.empty()
            every { fetchFinalRawOutput(urls[2], 0L, any()) } returns Observable.just(RawOutputChunk(" third".toByteArray()))
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

            presenter.logString.value shouldBe "first"
            scheduler.advanceTimeTo(6, SECONDS)
            presenter.logString.value shouldBe "first second"
            scheduler.advanceTimeTo(10, SECONDS)
            presenter.logString.value shouldBe "first second done third"
            presenter.logLoadFailed.value shouldBe false
            verify(exactly = 1) { view.showStep(initialStep) }
            verify(exactly = 3) { api.getBuild("github", "owner", "repo", 42) }
        } finally {
            scope?.destroy()
            RxAndroidPlugins.reset()
            RxJavaPlugins.reset()
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
                presenter.logString.value shouldBe (if (outputFails) "<br>[Some output could not be loaded.]<br>" else "done")
            } finally {
                scope?.destroy()
                RxAndroidPlugins.reset()
                RxJavaPlugins.reset()
            }
        }
    }

    listOf(false, true).forEach { outputFails ->
        it("preserves earlier live output when the final read fails: $outputFails") {
            val scheduler = TestScheduler()
            val runningStep = BuildStep("Run tests", listOf(action(5, null)))
            val finishedStep = BuildStep("Run tests", listOf(action(5, Date())))
            val runningBuild = build("running", listOf(runningStep))
            val finishedBuild = build("finished", listOf(finishedStep))
            val api = mockk<CircleCIAPIClientV1_1> {
                every { getBuild("github", "owner", "repo", 42) } returnsMany
                    listOf(Observable.just(runningBuild), Observable.just(finishedBuild))
            }
            val url = "https://circleci.com/api/private/output/raw/github/owner/repo/42/output/0/5"
            val output = mockk<BuildOutputClient> {
                every { fetchRawOutput(url, 0L) } returns Observable.just(RawOutputChunk("first".toByteArray()))
                every { fetchRawOutput(url, 5L) } returns Observable.just(RawOutputChunk(byteArrayOf()))
                every { fetchFinalRawOutput(url, 5L, any()) } returns if (outputFails) {
                    Observable.error(IOException("Final output unavailable"))
                } else {
                    Observable.just(RawOutputChunk(" last".toByteArray(), limitReached = true))
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
                verify(exactly = 1) { view.showStep(runningStep) }
                verify(exactly = 1) { output.fetchRawOutput(url, 0L) }
                presenter.logString.value shouldBe "first"
                scheduler.advanceTimeTo(4_999, MILLISECONDS)
                verify(exactly = 1) { api.getBuild("github", "owner", "repo", 42) }
                verify(exactly = 0) { output.fetchFinalRawOutput(any(), any(), any()) }

                scheduler.advanceTimeTo(5, SECONDS)
                verify(exactly = 2) { api.getBuild("github", "owner", "repo", 42) }

                verify(exactly = 1) { output.fetchFinalRawOutput(url, 5L, any()) }
                presenter.logLoadFailed.value shouldBe outputFails
                val expected = if (outputFails) "first" else "first last" + OUTPUT_LIMIT_MESSAGE.replace("\n", "<br>")
                presenter.logString.value shouldBe expected
            } finally {
                scope?.destroy()
                RxAndroidPlugins.reset()
                RxJavaPlugins.reset()
            }
        }
    }

    it("loads the selected step and its logs when the user retries after HTTP 503") {
        val scheduler = TestScheduler()
        val selectedUrl = "https://circleci.com/selected-output"
        val selectedAction = action(5, Date()).apply {
            every { outputUrl } returns selectedUrl
        }
        val otherStep = BuildStep("Run tests", listOf(action(2, Date())))
        val selectedStep = BuildStep("Run tests", listOf(selectedAction))
        val finishedBuild = build("finished", listOf(otherStep, selectedStep))
        val api = mockk<CircleCIAPIClientV1_1> {
            every { getBuild("github", "owner", "repo", 42) } returnsMany
                listOf(Observable.error(IOException("HTTP 503")), Observable.just(finishedBuild))
        }
        val output = mockk<BuildOutputClient> {
            every { fetchOutput(selectedUrl) } returns Observable.just("Selected step output")
        }
        val presenter = BuildStepPresenter { output }.apply {
            di = DI {
                bind<BuildId>() with singleton { BuildId("github", "owner", "repo", 42) }
                bind<BuildStepId>() with singleton { BuildStepId(5) }
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
            verify(exactly = 1) { api.getBuild("github", "owner", "repo", 42) }
            presenter.canRetryStepLoad.value shouldBe true

            presenter.retryStepLoad()
            presenter.retryStepLoad()

            verify(exactly = 2) { api.getBuild("github", "owner", "repo", 42) }
            verify(exactly = 1) { view.showStep(selectedStep) }
            failures.assertValues(false, true, false)
            presenter.canRetryStepLoad.value shouldBe false
            presenter.logString.value shouldBe "Selected step output"
        } finally {
            scope?.destroy()
            failures.dispose()
            RxAndroidPlugins.reset()
            RxJavaPlugins.reset()
        }
    }
})
