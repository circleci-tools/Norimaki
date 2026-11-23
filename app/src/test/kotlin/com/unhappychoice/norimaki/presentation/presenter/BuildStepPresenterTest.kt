package com.unhappychoice.norimaki.presentation.presenter

import com.github.unhappychoice.circleci.CircleCIAPIClientV1_1
import com.unhappychoice.norimaki.MainActivity
import com.github.unhappychoice.circleci.v1.response.Build
import com.github.unhappychoice.circleci.v1.response.BuildAction
import com.github.unhappychoice.circleci.v1.response.BuildStep
import com.unhappychoice.norimaki.domain.model.BuildId
import com.unhappychoice.norimaki.domain.model.BuildStepId
import com.unhappychoice.norimaki.domain.service.SessionService
import com.unhappychoice.norimaki.infrastructure.output.BuildOutputClient
import com.unhappychoice.norimaki.infrastructure.output.OUTPUT_LIMIT_MESSAGE
import com.unhappychoice.norimaki.infrastructure.output.RawOutputChunk
import com.unhappychoice.norimaki.presentation.log.LogChunkFormatter
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

        it("should strip split hyperlink controls independently for each action") {
            completedActionLogs(listOf("first", "second")) { url ->
                if (url == "first") {
                    Observable.just("before \u001B", "]8;id=hidden;https://example.test\u001B", "\\Report\u001B]8;;")
                } else {
                    Observable.just("other output")
                }
            }.test().assertResult("before ", "Report", "other output")
        }

        it("should continue when creating a download throws") {
            completedActionLogs(listOf("invalid", "valid")) { url ->
                if (url == "invalid") throw IllegalArgumentException("Invalid URL")
                Observable.just(url)
            }.test().assertResult("\n[Some output could not be loaded.]\n", "valid")
        }
    }

    describe("BuildStepPresenter running output") {
        val scenarios = listOf(
            "selected step", "build", "build with the selected step missing",
            "final download failure", "failure without output", "split hyperlink"
        )
        scenarios.forEach { scenario ->
            it("updates log search when polling ends with $scenario") {
                val scheduler = TestScheduler()
                val runningStep = BuildStep("Run tests", listOf(action(5, null)))
                val first = BuildStep("Run tests", listOf(action(2, Date())))
                val running = build("running", listOf(first, runningStep))
                val finishedStep = BuildStep("Run tests", listOf(action(5, Date())))
                val runningSnapshot = if (scenario == "build with the selected step missing") {
                    build("running", listOf(first))
                } else {
                    running
                }
                val finished = when (scenario) {
                    "build" -> build("finished", listOf(first, runningStep))
                    "build with the selected step missing" -> build("finished", listOf(first))
                    else -> build("running", listOf(first, finishedStep))
                }
                val finalDownloadFails = scenario.contains("failure")
                val hasOutput = scenario != "failure without output"
                val liveChunks = when (scenario) {
                    "split hyperlink" -> listOf(
                        "before \u001B", "]0;hidden ", "title\u0007\u001B]8;id=hidden", ";https://example.test", "\u001B"
                    )
                    "failure without output" -> emptyList()
                    else -> (0..9).map(Int::toString)
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
                        listOf(Observable.just(running), Observable.just(runningSnapshot), Observable.just(finished))
                }
                val url = "https://circleci.com/api/private/output/raw/github/owner/repo/42/output/0/5"
                val output = mockk<BuildOutputClient> {
                    every { fetchRawOutput(url, any()) } answers {
                        Observable.just(RawOutputChunk(chunksByOffset[secondArg<Long>()] ?: byteArrayOf()))
                    }
                    every { fetchFinalRawOutput(url, finalOffset, any()) } returns if (finalDownloadFails) {
                        Observable.error(IOException("HTTP 503"))
                    } else {
                        Observable.just(RawOutputChunk(finalChunk.toByteArray()))
                    }
                }
                val presenter = BuildStepPresenter { output }.apply {
                    di = DI {
                        bind<BuildId>() with singleton { BuildId("github", "owner", "repo", 42) }
                        bind<BuildStepId>() with singleton { BuildStepId(5) }
                        bind<MainActivity>() with singleton {
                            mockk<MainActivity>().also { PollingLifecycle(it).start() }
                        }
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

                    scheduler.advanceTimeTo(4, SECONDS)
                    verify(exactly = 1) { api.getBuild("github", "owner", "repo", 42) }

                    scheduler.advanceTimeTo(5, SECONDS)
                    verify(exactly = 2) { api.getBuild("github", "owner", "repo", 42) }
                    verify(exactly = 0) { output.fetchFinalRawOutput(any(), any(), any()) }

                    scheduler.advanceTimeTo(9, SECONDS)
                    verify(exactly = 1) { view.showStep(runningStep) }
                    val retainedLog = when (scenario) {
                        "split hyperlink" -> "before "
                        "failure without output" -> ""
                        else -> "0123456789"
                    }
                    presenter.logLines().joinToString("\n") shouldBe retainedLog
                    presenter.logSearchAvailable.value shouldBe false
                    presenter.logLoadFailed.value shouldBe false

                    scheduler.advanceTimeTo(10, SECONDS)
                    verify(exactly = 3) { api.getBuild("github", "owner", "repo", 42) }
                    verify(exactly = 1) { output.fetchFinalRawOutput(url, finalOffset, any()) }
                    presenter.logLoadFailed.value shouldBe finalDownloadFails
                    val expectedFinalLog = when {
                        finalDownloadFails -> retainedLog
                        scenario == "split hyperlink" -> "before Report"
                        else -> retainedLog + "done"
                    }
                    presenter.logLines().joinToString("\n") shouldBe expectedFinalLog
                    presenter.logSearchAvailable.value shouldBe hasOutput
                } finally {
                    scope?.destroy()
                    RxAndroidPlugins.reset()
                    RxJavaPlugins.reset()
                }
            }
        }
    }

    listOf("initial load", "a later snapshot").forEach { completion ->
        it("catches up an action completed at $completion and only revisits it at step completion") {
            val scheduler = TestScheduler()
            val initiallyCompleted = completion == "initial load"
            val running = action(5, null, 1)
            val completed = action(5, Date())
            val initialStep = BuildStep("Run tests", listOf(if (initiallyCompleted) completed else action(5, null), running))
            val updatedStep = BuildStep("Run tests", listOf(completed, running))
            val finishedStep = BuildStep("Run tests", listOf(completed, action(5, Date(), 1)))
            val api = mockk<CircleCIAPIClientV1_1> {
                every { getBuild("github", "owner", "repo", 42) } returnsMany listOf(
                    Observable.just(build("running", listOf(updatedStep))),
                    Observable.just(build("finished", listOf(finishedStep)))
                )
            }
            val firstUrl = "https://circleci.com/api/private/output/raw/github/owner/repo/42/output/0/5"
            val runningUrl = "https://circleci.com/api/private/output/raw/github/owner/repo/42/output/1/5"
            val output = mockk<BuildOutputClient> {
                every { fetchRawOutput(firstUrl, any()) } answers {
                    Observable.just(RawOutputChunk("start".drop(secondArg<Long>().toInt()).toByteArray()))
                }
                every { fetchRawOutput(runningUrl, any()) } returns Observable.just(RawOutputChunk("x".toByteArray()))
                val catchUpOffset = if (initiallyCompleted) 0L else 5L
                every { fetchFinalRawOutput(firstUrl, catchUpOffset, any()) } returns
                    Observable.just(RawOutputChunk("start done".drop(catchUpOffset.toInt()).toByteArray()))
                every { fetchFinalRawOutput(firstUrl, 10L, any()) } returns
                    Observable.just(RawOutputChunk(" late".toByteArray()))
                every { fetchFinalRawOutput(runningUrl, any(), any()) } returns
                    Observable.just(RawOutputChunk("y".toByteArray()))
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
                val observer = presenter.runningOutput(build("running", listOf(initialStep)), initialStep, api, output).test()
                try {
                    scheduler.advanceTimeTo(6, SECONDS)
                    val firstRequests = if (initiallyCompleted) 0 else 5
                    verify(exactly = firstRequests) { output.fetchRawOutput(firstUrl, any()) }
                    verify(exactly = 1) { output.fetchFinalRawOutput(firstUrl, any(), any()) }
                    observer.values().filterNot { it == "x" }.joinToString("") shouldBe "start done"

                    scheduler.advanceTimeTo(9, SECONDS)
                    observer.assertNotComplete().assertNoErrors()
                    verify(exactly = firstRequests) { output.fetchRawOutput(firstUrl, any()) }
                    verify(exactly = 1) { output.fetchFinalRawOutput(firstUrl, any(), any()) }
                    verify(exactly = 10) { output.fetchRawOutput(runningUrl, any()) }

                    scheduler.advanceTimeTo(10, SECONDS)
                    observer.assertComplete().assertNoErrors()
                    verify(exactly = 1) { output.fetchFinalRawOutput(firstUrl, 10L, any()) }
                    observer.values().filterNot { it == "x" || it == "y" }.joinToString("") shouldBe "start done late"
                    observer.values().last() shouldBe "y"
                } finally {
                    observer.dispose()
                }
            } finally {
                RxJavaPlugins.setComputationSchedulerHandler(previousScheduler)
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
                bind<MainActivity>() with singleton {
                    mockk<MainActivity>().also { PollingLifecycle(it).start() }
                }
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

            scheduler.triggerActions()
            presenter.logLines() shouldBe listOf("first")
            scheduler.advanceTimeTo(6, SECONDS)
            presenter.logLines() shouldBe listOf("first second")
            scheduler.advanceTimeTo(10, SECONDS)
            presenter.logLines() shouldBe listOf("first second done third")
            presenter.logSearchAvailable.value shouldBe true
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
                    bind<MainActivity>() with singleton {
                        mockk<MainActivity>().also { PollingLifecycle(it).start() }
                    }
                    bind<SessionService>() with singleton { testSession(api) }
                }
            }
            val view = mockk<BuildStepView>(relaxUnitFun = true)
            var scope: MortarScope? = null

            try {
                RxJavaPlugins.setIoSchedulerHandler { Schedulers.trampoline() }
                RxJavaPlugins.setComputationSchedulerHandler { Schedulers.trampoline() }
                RxAndroidPlugins.setInitMainThreadSchedulerHandler { Schedulers.trampoline() }
                RxAndroidPlugins.setMainThreadSchedulerHandler { Schedulers.trampoline() }

                scope = presenter.attachToTestScope(view)

                verify(exactly = 1) { view.showStep(finishedStep) }
                presenter.logLoadFailed.value shouldBe false
                presenter.logLines() shouldBe if (outputFails) listOf("", "[Some output could not be loaded.]", "") else listOf("done")
                presenter.logSearchAvailable.value shouldBe true
                verify(exactly = 1) { output.fetchOutput(expectedUrl) }
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
                    Observable.just(RawOutputChunk(" last\u001B]8;;https://example.test".toByteArray(), limitReached = true))
                }
            }
            val presenter = BuildStepPresenter { output }.apply {
                di = DI {
                    bind<BuildId>() with singleton { BuildId("github", "owner", "repo", 42) }
                    bind<BuildStepId>() with singleton { BuildStepId(5) }
                    bind<MainActivity>() with singleton {
                        mockk<MainActivity>().also { PollingLifecycle(it).start() }
                    }
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
                scheduler.triggerActions()
                verify(exactly = 1) { output.fetchRawOutput(url, 0L) }
                presenter.logLines() shouldBe listOf("first")
                scheduler.advanceTimeTo(4_999, MILLISECONDS)
                verify(exactly = 1) { api.getBuild("github", "owner", "repo", 42) }
                verify(exactly = 0) { output.fetchFinalRawOutput(any(), any(), any()) }

                scheduler.advanceTimeTo(5, SECONDS)
                verify(exactly = 2) { api.getBuild("github", "owner", "repo", 42) }

                verify(exactly = 1) { output.fetchFinalRawOutput(url, 5L, any()) }
                presenter.logLoadFailed.value shouldBe outputFails
                val expected = if (outputFails) "first" else "first last" + OUTPUT_LIMIT_MESSAGE
                presenter.logLines() shouldBe expected.split('\n')
                presenter.logSearchAvailable.value shouldBe true
            } finally {
                scope?.destroy()
                RxAndroidPlugins.reset()
                RxJavaPlugins.reset()
            }
        }
    }

    it("pauses with the view attached and resumes live and final reads at the saved offsets") {
        val scheduler = TestScheduler()
        val formatting = TestScheduler()
        val activity = mockk<MainActivity>()
        val lifecycle = PollingLifecycle(activity).apply { start() }
        val runningStep = BuildStep("Run tests", listOf(action(5, null)))
        val finishedStep = BuildStep("Run tests", listOf(action(5, Date())))
        val running = build("running", listOf(runningStep))
        val finished = build("finished", listOf(finishedStep))
        var current = running
        val api = mockk<CircleCIAPIClientV1_1> {
            every { getBuild("github", "owner", "repo", 42) } answers { Observable.just(current) }
        }
        val url = "https://circleci.com/api/private/output/raw/github/owner/repo/42/output/0/5"
        val beforeStop = "\u001B[32mfirst\u001B]8;;https://example.test\u001B".toByteArray()
        val afterStart = "\\ next\u001B]8;;\u0007".toByteArray()
        val finalOffset = beforeStop.size.toLong() + afterStart.size
        val output = mockk<BuildOutputClient> {
            every { fetchRawOutput(url, 0L) } returns Observable.just(RawOutputChunk(beforeStop))
            every { fetchRawOutput(url, beforeStop.size.toLong()) } returns Observable.just(RawOutputChunk(afterStart))
            every { fetchRawOutput(url, finalOffset) } returns Observable.just(RawOutputChunk(byteArrayOf()))
            every { fetchFinalRawOutput(url, finalOffset, any()) } returns
                Observable.just(RawOutputChunk(" done".toByteArray()))
        }
        val presenter = BuildStepPresenter({ formatting }, LogChunkFormatter::format) { output }.apply {
            di = DI {
                bind<BuildId>() with singleton { BuildId("github", "owner", "repo", 42) }
                bind<BuildStepId>() with singleton { BuildStepId(5) }
                bind<MainActivity>() with singleton { activity }
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
            presenter.logLines() shouldBe emptyList()

            presenter.logSearchAvailable.value shouldBe false
            lifecycle.stop()
            scheduler.advanceTimeBy(30, SECONDS)
            verify(exactly = 1) { api.getBuild("github", "owner", "repo", 42) }
            verify(exactly = 1) { output.fetchRawOutput(any(), any()) }

            formatting.triggerActions()
            presenter.logLines() shouldBe emptyList()

            lifecycle.start()
            verify(exactly = 1) { output.fetchRawOutput(url, beforeStop.size.toLong()) }
            formatting.triggerActions()
            presenter.logLines() shouldBe listOf("first next")
            verify(exactly = 2) { api.getBuild("github", "owner", "repo", 42) }
            scheduler.advanceTimeBy(1, SECONDS)
            verify(exactly = 3) { output.fetchRawOutput(any(), any()) }
            scheduler.advanceTimeBy(4, SECONDS)
            verify(exactly = 3) { api.getBuild("github", "owner", "repo", 42) }

            lifecycle.stop()
            current = finished
            scheduler.advanceTimeBy(30, SECONDS)
            verify(exactly = 3) { api.getBuild("github", "owner", "repo", 42) }
            verify(exactly = 7) { output.fetchRawOutput(any(), any()) }
            verify(exactly = 0) { output.fetchFinalRawOutput(any(), any(), any()) }

            lifecycle.start()
            verify(exactly = 1) { view.showStep(finishedStep) }
            verify(exactly = 1) { output.fetchFinalRawOutput(url, finalOffset, any()) }
            formatting.triggerActions()
            presenter.logLines() shouldBe listOf("first next done")
            presenter.log[0].map { it.color }.distinct() shouldBe listOf(0xFF00F000.toInt())
            presenter.logSearchAvailable.value shouldBe true
            scheduler.advanceTimeBy(30, SECONDS)
            verify(exactly = 4) { api.getBuild("github", "owner", "repo", 42) }
            verify(exactly = 7) { output.fetchRawOutput(any(), any()) }
        } finally {
            scope?.destroy()
            RxAndroidPlugins.reset()
            RxJavaPlugins.reset()
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
                bind<MainActivity>() with singleton {
                    mockk<MainActivity>().also { PollingLifecycle(it).start() }
                }
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
            scheduler.triggerActions()
            presenter.logLines() shouldBe listOf("Selected step output")
            presenter.logSearchAvailable.value shouldBe true
        } finally {
            scope?.destroy()
            failures.dispose()
            RxAndroidPlugins.reset()
            RxJavaPlugins.reset()
        }
    }
})

private fun BuildStepPresenter.logLines(): List<String> =
    (0 until log.lineCount).map { line -> log[line].joinToString("") { it.text } }
