package com.unhappychoice.norimaki.presentation.presenter

import com.github.unhappychoice.circleci.CircleCIAPIClientV1_1
import com.github.unhappychoice.circleci.v1.response.Build
import com.github.unhappychoice.circleci.v1.response.BuildStep
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import com.unhappychoice.norimaki.MainActivity
import com.unhappychoice.norimaki.domain.model.BuildId
import com.unhappychoice.norimaki.domain.model.BuildStepId
import com.unhappychoice.norimaki.presentation.screen.BuildStepScreen
import flow.Flow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import io.reactivex.Observable
import io.reactivex.android.plugins.RxAndroidPlugins
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.schedulers.Schedulers
import io.reactivex.schedulers.TestScheduler
import io.reactivex.subjects.PublishSubject
import java.util.concurrent.TimeUnit
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.singleton

class BuildPresenterTest : DescribeSpec({
    lateinit var presenter: BuildPresenter
    lateinit var api: CircleCIAPIClientV1_1
    lateinit var retry: PublishSubject<Build>
    lateinit var deleteCache: PublishSubject<Unit>

    beforeEach {
        RxJavaPlugins.setIoSchedulerHandler { Schedulers.trampoline() }
        RxAndroidPlugins.setInitMainThreadSchedulerHandler { Schedulers.trampoline() }
        RxAndroidPlugins.setMainThreadSchedulerHandler { Schedulers.trampoline() }

        retry = PublishSubject.create()
        deleteCache = PublishSubject.create()
        api = mockk {
            every { retryBuild("github", "owner", "repo", 123) } answers { retry }
            every { deleteCache("github", "owner", "repo") } answers { deleteCache }
        }
        presenter = BuildPresenter().apply {
            di = DI {
                bind<BuildId>() with singleton { BuildId("github", "owner", "repo", 123) }
                bind<CircleCIAPIClientV1_1>() with singleton { api }
            }
        }
    }

    afterEach {
        presenter.bag.clear()
        RxAndroidPlugins.reset()
        RxJavaPlugins.reset()
    }

    describe("rebuild submissions") {
        it("should ignore both actions during retry and allow a submission after retry fails") {
            presenter.rebuild()
            presenter.rebuild()
            presenter.rebuildWithoutCache()

            verify(exactly = 1) { api.retryBuild(any(), any(), any(), any()) }
            verify(exactly = 0) { api.deleteCache(any(), any(), any()) }

            retry.onError(IllegalStateException("Retry failed"))
            presenter.rebuildWithoutCache()

            verify(exactly = 1) { api.deleteCache(any(), any(), any()) }
        }

        it("should ignore both actions during cache deletion and the following retry") {
            presenter.rebuildWithoutCache()
            presenter.rebuildWithoutCache()
            presenter.rebuild()

            verify(exactly = 1) { api.deleteCache(any(), any(), any()) }
            verify(exactly = 0) { api.retryBuild(any(), any(), any(), any()) }

            deleteCache.onNext(Unit)
            deleteCache.onComplete()
            presenter.rebuildWithoutCache()
            presenter.rebuild()

            verify(exactly = 1) { api.deleteCache(any(), any(), any()) }
            verify(exactly = 1) { api.retryBuild(any(), any(), any(), any()) }
        }

        it("should allow a submission after cache deletion fails") {
            presenter.rebuildWithoutCache()
            deleteCache.onError(IllegalStateException("Cache deletion failed"))
            verify(exactly = 0) { api.retryBuild(any(), any(), any(), any()) }

            presenter.rebuild()

            verify(exactly = 1) { api.retryBuild(any(), any(), any(), any()) }
        }
    }
})

class BuildPresenterPollingTest : DescribeSpec({
    it("should only publish changed snapshots and open the latest step") {
        val scheduler = TestScheduler()
        val first = step("https://example.com/first")
        val duplicate = step("https://example.com/first")
        val changed = step("https://example.com/latest")
        (first == duplicate && first !== duplicate) shouldBe true

        val buildId = BuildId("github", "owner", "repo", 42)
        val builds = listOf(pollResult(first), pollResult(duplicate), pollResult(changed, "finished"))
        val api = mockk<CircleCIAPIClientV1_1>()
        every { api.getBuild("github", "owner", "repo", 42) } returnsMany
            builds.map { Observable.just(it) }

        val activity = mockk<MainActivity>()
        val presenter = BuildPresenter().apply {
            di = DI {
                bind<BuildId>() with singleton { buildId }
                bind<MainActivity>() with singleton { activity }
                bind<CircleCIAPIClientV1_1>() with singleton { api }
            }
        }
        val snapshots = presenter.steps.asObservable().test()

        try {
            RxJavaPlugins.setIoSchedulerHandler { Schedulers.trampoline() }
            RxJavaPlugins.setComputationSchedulerHandler { scheduler }
            RxAndroidPlugins.setInitMainThreadSchedulerHandler { Schedulers.trampoline() }
            RxAndroidPlugins.setMainThreadSchedulerHandler { Schedulers.trampoline() }

            val loadedBuilds = mutableListOf<Build>()
            presenter.pollBuild { loadedBuilds.add(it) }
            snapshots.assertValues(emptyList(), listOf(first))

            scheduler.advanceTimeBy(BuildPresenter.BUILD_POLLING_SECONDS, TimeUnit.SECONDS)
            verify(exactly = 2) { api.getBuild("github", "owner", "repo", 42) }
            snapshots.assertValues(emptyList(), listOf(first))

            scheduler.advanceTimeBy(BuildPresenter.BUILD_POLLING_SECONDS, TimeUnit.SECONDS)
            snapshots.assertValues(emptyList(), listOf(first), listOf(changed))
            loadedBuilds shouldBe builds

            val flow = mockk<Flow>(relaxed = true)
            val screen = slot<BuildStepScreen>()
            mockkStatic(Flow::class)
            every { Flow.get(activity) } returns flow
            val latestSteps = snapshots.values().last()
            presenter.goToBuildStepScreen(BuildStepId.from(latestSteps.single(), position = 0))
            verify(exactly = 1) { flow.set(capture(screen)) }
            screen.captured.buildId shouldBe buildId
            (screen.captured.stepId.resolve(loadedBuilds.last().steps) === changed) shouldBe true
        } finally {
            presenter.bag.clear()
            snapshots.dispose()
            unmockkStatic(Flow::class)
            RxAndroidPlugins.reset()
            RxJavaPlugins.reset()
        }
    }
})

private fun step(outputUrl: String): BuildStep = GsonBuilder()
    .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
    .create()
    .fromJson(
        """{"name":"test","actions":[{"output_url":"$outputUrl"}]}""",
        BuildStep::class.java
    )

private fun pollResult(step: BuildStep, state: String = "running"): Build = mockk {
    every { lifecycle } returns state
    every { steps } returns listOf(step)
    every { workflows } returns null
}
