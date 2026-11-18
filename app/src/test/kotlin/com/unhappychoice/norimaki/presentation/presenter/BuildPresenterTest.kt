package com.unhappychoice.norimaki.presentation.presenter

import com.github.unhappychoice.circleci.CircleCIAPIClientV1_1
import com.github.unhappychoice.circleci.v1.response.Build
import com.github.unhappychoice.circleci.v1.response.BuildStep
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import com.unhappychoice.norimaki.MainActivity
import com.unhappychoice.norimaki.domain.service.SessionService
import com.unhappychoice.norimaki.presentation.screen.BuildStepScreen
import com.unhappychoice.norimaki.presentation.view.BuildView
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
import mortar.MortarScope
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.singleton
import java.util.concurrent.TimeUnit

class BuildPresenterTest : DescribeSpec({
    lateinit var scope: MortarScope
    lateinit var presenter: BuildPresenter
    lateinit var view: BuildView
    lateinit var session: SessionService
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
            every { getBuild("github", "owner", "repo", 123) } returns Observable.never()
            every { retryBuild("github", "owner", "repo", 123) } answers { retry }
            every { deleteCache("github", "owner", "repo") } answers { deleteCache }
        }
        val build = mockk<Build> {
            every { vcsUrl } returns "https://github.com/owner/repo"
            every { username } returns "owner"
            every { reponame } returns "repo"
            every { buildNum } returns 123
        }
        session = testSession(api)
        presenter = BuildPresenter().apply {
            di = DI {
                bind<Build>() with singleton { build }
                bind<SessionService>() with singleton { session }
            }
        }
        view = mockk()
        scope = presenter.attachToTestScope(view)
    }

    afterEach {
        scope.destroy()
        RxAndroidPlugins.reset()
        RxJavaPlugins.reset()
    }

    describe("rebuild submissions") {
        it("cancels cache deletion and blocks rebuilds when the session changes") {
            presenter.rebuildWithoutCache()
            deleteCache.hasObservers() shouldBe true

            session.replace("replacement-token").test().assertComplete()
            deleteCache.hasObservers() shouldBe false
            presenter.rebuild()

            verify(exactly = 0) { api.retryBuild(any(), any(), any(), any()) }
        }

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

        it("should complete the cache-free rebuild after detachment") {
            presenter.rebuildWithoutCache()
            presenter.dropView(view)

            deleteCache.onNext(Unit)
            retry.onComplete()
            verify(exactly = 1) { api.deleteCache(any(), any(), any()) }
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

        val build = pollResult(first)
        val api = mockk<CircleCIAPIClientV1_1>()
        every { api.getBuild("github", "owner", "repo", 42) } returnsMany
            listOf(build, pollResult(duplicate), pollResult(changed, "finished")).map { Observable.just(it) }

        val activity = mockk<MainActivity>()
        val presenter = BuildPresenter().apply {
            di = DI {
                bind<Build>() with singleton { build }
                bind<MainActivity>() with singleton { activity }
                bind<SessionService>() with singleton { testSession(api) }
            }
        }
        val snapshots = presenter.steps.asObservable().test()

        var scope: MortarScope? = null
        try {
            RxJavaPlugins.setIoSchedulerHandler { Schedulers.trampoline() }
            RxJavaPlugins.setComputationSchedulerHandler { scheduler }
            RxAndroidPlugins.setInitMainThreadSchedulerHandler { Schedulers.trampoline() }
            RxAndroidPlugins.setMainThreadSchedulerHandler { Schedulers.trampoline() }

            scope = presenter.attachToTestScope(mockk<BuildView>())
            snapshots.assertValues(emptyList(), listOf(first))

            scheduler.advanceTimeBy(BuildPresenter.BUILD_POLLING_SECONDS, TimeUnit.SECONDS)
            verify(exactly = 2) { api.getBuild("github", "owner", "repo", 42) }
            snapshots.assertValues(emptyList(), listOf(first))

            scheduler.advanceTimeBy(BuildPresenter.BUILD_POLLING_SECONDS, TimeUnit.SECONDS)
            snapshots.assertValues(emptyList(), listOf(first), listOf(changed))

            val flow = mockk<Flow>(relaxed = true)
            val screen = slot<BuildStepScreen>()
            mockkStatic(Flow::class)
            every { Flow.get(activity) } returns flow
            presenter.goToBuildStepScreen(snapshots.values().last().single())
            verify(exactly = 1) { flow.set(capture(screen)) }
            (screen.captured.buildStep === changed) shouldBe true
        } finally {
            scope?.destroy()
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
    every { vcsUrl } returns "https://github.com/owner/repo"
    every { username } returns "owner"
    every { reponame } returns "repo"
    every { buildNum } returns 42
}
