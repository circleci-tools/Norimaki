package com.unhappychoice.norimaki.presentation.presenter

import com.github.unhappychoice.circleci.CircleCIAPIClientV1_1
import com.github.unhappychoice.circleci.v1.response.Build
import com.github.unhappychoice.circleci.v1.response.BuildAction
import com.github.unhappychoice.circleci.v1.response.BuildStep
import com.unhappychoice.norimaki.domain.model.BuildId
import com.unhappychoice.norimaki.domain.model.BuildStepId
import com.unhappychoice.norimaki.infrastructure.output.BuildOutputClient
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
import java.util.Date
import java.util.concurrent.TimeUnit.SECONDS
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.singleton

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
                        Observable.just(secondArg<Long>().toString().toByteArray())
                    }
                    every { fetchFinalRawOutput(url, 5L) } returns Observable.just("done".toByteArray())
                }
                val presenter = BuildStepPresenter().apply {
                    di = DI {
                        bind<BuildId>() with singleton { BuildId("github", "owner", "repo", 42) }
                        bind<BuildStepId>() with singleton { BuildStepId(5) }
                        bind<CircleCIAPIClientV1_1>() with singleton { api }
                        bind<BuildOutputClient>() with singleton { output }
                    }
                }

                val previousScheduler = RxJavaPlugins.getComputationSchedulerHandler()
                RxJavaPlugins.setComputationSchedulerHandler { scheduler }
                try {
                    val observer = presenter.runningOutput(running, second).test()
                    try {
                        scheduler.advanceTimeBy(4, SECONDS)
                        observer.assertNotComplete().assertNoErrors()
                        observer.values() shouldBe listOf("0", "1", "2", "3", "4")

                        scheduler.advanceTimeBy(1, SECONDS)
                        observer.assertComplete().assertNoErrors()
                        observer.values() shouldBe listOf("0", "1", "2", "3", "4", "done")
                    } finally {
                        observer.dispose()
                    }
                } finally {
                    RxJavaPlugins.setComputationSchedulerHandler(previousScheduler)
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
            val presenter = BuildStepPresenter().apply {
                di = DI {
                    bind<BuildId>() with singleton { BuildId("github", "owner", "repo", 42) }
                    bind<BuildStepId>() with singleton { BuildStepId(5) }
                    bind<CircleCIAPIClientV1_1>() with singleton { api }
                    bind<BuildOutputClient>() with singleton { output }
                }
            }
            val loaded = mutableListOf<BuildStep>()

            try {
                RxJavaPlugins.setIoSchedulerHandler { Schedulers.trampoline() }
                RxAndroidPlugins.setInitMainThreadSchedulerHandler { Schedulers.trampoline() }
                RxAndroidPlugins.setMainThreadSchedulerHandler { Schedulers.trampoline() }

                presenter.loadStep { loaded.add(it) }

                loaded shouldBe listOf(finishedStep)
                presenter.logLoadFailed.value shouldBe outputFails
                presenter.logString.value shouldBe (if (outputFails) "" else "done")
            } finally {
                presenter.bag.clear()
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
                bind<CircleCIAPIClientV1_1>() with singleton { api }
            }
        }
        val failures = presenter.logLoadFailed.asObservable().distinctUntilChanged().test()
        var stepLoaded = false

        try {
            RxJavaPlugins.setComputationSchedulerHandler { scheduler }
            RxJavaPlugins.setIoSchedulerHandler { Schedulers.trampoline() }
            RxAndroidPlugins.setInitMainThreadSchedulerHandler { Schedulers.trampoline() }
            RxAndroidPlugins.setMainThreadSchedulerHandler { Schedulers.trampoline() }

            presenter.loadStep { stepLoaded = true }

            failures.assertValues(false, true)
            stepLoaded shouldBe false
            scheduler.advanceTimeBy(BuildPresenter.BUILD_POLLING_SECONDS * 3, SECONDS)
            verify(exactly = 1) { api.getBuild("github", "owner", "repo", 123) }
        } finally {
            presenter.bag.clear()
            failures.dispose()
            RxAndroidPlugins.reset()
            RxJavaPlugins.reset()
        }
    }
})
