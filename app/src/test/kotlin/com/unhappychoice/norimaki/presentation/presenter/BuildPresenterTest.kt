package com.unhappychoice.norimaki.presentation.presenter

import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.widget.Toolbar
import androidx.drawerlayout.widget.DrawerLayout
import com.github.unhappychoice.circleci.CircleCIAPIClientV1_1
import com.github.unhappychoice.circleci.v1.response.Build
import com.github.unhappychoice.circleci.v1.response.BuildStep
import com.github.unhappychoice.circleci.v1.response.Workflow
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import com.unhappychoice.norimaki.MainActivity
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.databinding.ActivityMainBinding
import com.unhappychoice.norimaki.domain.model.BuildId
import com.unhappychoice.norimaki.domain.model.BuildStepId
import com.unhappychoice.norimaki.domain.service.SessionService
import com.unhappychoice.norimaki.presentation.screen.BuildStepScreen
import com.unhappychoice.norimaki.presentation.screen.TestResultsScreen
import com.unhappychoice.norimaki.presentation.screen.WorkflowScreen
import com.unhappychoice.norimaki.presentation.view.BuildView
import com.unhappychoice.norimaki.presentation.view.NavigationView
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
    lateinit var activity: MainActivity
    lateinit var session: SessionService
    lateinit var api: CircleCIAPIClientV1_1
    lateinit var initialBuild: PublishSubject<Build>
    lateinit var retry: PublishSubject<Build>
    lateinit var deleteCache: PublishSubject<Unit>

    fun createPresenter(number: Int = 123) = BuildPresenter().apply {
        di = DI {
            bind<MainActivity>() with singleton { activity }
            bind<BuildId>() with singleton { BuildId("github", "owner", "repo", number) }
            bind<SessionService>() with singleton { session }
        }
    }

    beforeEach {
        RxJavaPlugins.setIoSchedulerHandler { Schedulers.trampoline() }
        RxAndroidPlugins.setInitMainThreadSchedulerHandler { Schedulers.trampoline() }
        RxAndroidPlugins.setMainThreadSchedulerHandler { Schedulers.trampoline() }

        initialBuild = PublishSubject.create()
        retry = PublishSubject.create()
        deleteCache = PublishSubject.create()
        api = mockk {
            every { getBuild("github", "owner", "repo", 123) } returns initialBuild
            every { retryBuild("github", "owner", "repo", 123) } answers { retry }
            every { deleteCache("github", "owner", "repo") } answers { deleteCache }
        }
        session = testSession(api)
        activity = mockk()
        presenter = createPresenter()
        view = mockk(relaxUnitFun = true)
        scope = presenter.attachToTestScope(view)
    }

    afterEach {
        scope.destroy()
        RxAndroidPlugins.reset()
        RxJavaPlugins.reset()
    }

    it("ignores artifact navigation after the verified session ends") {
        session.replace("replacement-token").test().assertComplete()

        presenter.goToArtifactsScreen()

        verify { activity wasNot Called }
    }

    describe("loading build details") {
        it("should wait for build details before opening test results") {
            val flow = mockk<Flow>(relaxed = true)
            val screen = slot<TestResultsScreen>()
            mockkStatic(Flow::class)
            try {
                every { Flow.get(activity) } returns flow
                presenter.goToTestResultsScreen()
                verify(exactly = 0) { flow.set(any()) }

                val build = pollResult(step("https://example.com/output"), "finished")
                initialBuild.onNext(build)
                presenter.goToTestResultsScreen()

                verify(exactly = 1) { flow.set(capture(screen)) }
                screen.captured.build shouldBe build
            } finally {
                unmockkStatic(Flow::class)
            }
        }

        it("should expose the initial failure and reload the same build on retry") {
            val scheduler = TestScheduler()
            RxJavaPlugins.setComputationSchedulerHandler { scheduler }
            val response = PublishSubject.create<Build>()
            val loadedStep = step("https://example.com/output")
            val build = pollResult(loadedStep, "finished")
            every { api.getBuild("github", "owner", "repo", 123) } returns response

            val failures = presenter.buildLoadFailed.asObservable().distinctUntilChanged().test()
            initialBuild.onError(IllegalStateException("Build load failed"))

            failures.assertValues(false, true)
            verify(exactly = 0) { view.showBuild(any()) }
            scheduler.advanceTimeBy(BuildPresenter.BUILD_POLLING_SECONDS, TimeUnit.SECONDS)
            verify(exactly = 1) { api.getBuild("github", "owner", "repo", 123) }

            presenter.pollBuild()
            failures.assertValues(false, true, false)
            response.onNext(build)

            verify(exactly = 1) { view.showBuild(build) }
            presenter.steps.value shouldBe listOf(loadedStep)
            verify(exactly = 2) { api.getBuild("github", "owner", "repo", 123) }
        }
    }

    describe("rebuild submissions") {
        mapOf(
            "returns to the previous screen while the originating build is active" to false,
            "keeps the workflow screen open while the originating build is still attached" to true
        ).forEach { (description, opensWorkflow) ->
            it(description) {
                val container = mockk<FrameLayout>()
                every { container.childCount } returns 1
                every { container.getChildAt(0) } returns view
                every { activity.binding } returns bindingWithContainer(container)
                every { view.isAttachedToWindow } returns true
                val flow = mockk<Flow>(relaxed = true)

                mockkStatic(Flow::class)
                try {
                    every { Flow.get(activity) } returns flow
                    presenter.rebuild()
                    retry.hasObservers() shouldBe true

                    if (opensWorkflow) every { container.childCount } returns 2
                    retry.onNext(mockk())
                    retry.onComplete()

                    verify(exactly = if (opensWorkflow) 0 else 1) { flow.goBack() }
                } finally {
                    unmockkStatic(Flow::class)
                }
            }
        }

        it("does not submit a rebuild after the view detaches") {
            presenter.dropView(view)

            presenter.rebuild()
            presenter.rebuildWithoutCache()

            verify(exactly = 0) { api.retryBuild(any(), any(), any(), any()) }
            verify(exactly = 0) { api.deleteCache(any(), any(), any()) }
            verify { activity wasNot Called }
        }

        it("identifies the cache project before build details load") {
            presenter.projectName shouldBe "github/owner/repo"
            verify(exactly = 0) { view.showBuild(any()) }
        }

        it("cancels cache deletion and blocks rebuilds when the session changes") {
            initialBuild.hasObservers() shouldBe true
            presenter.rebuildWithoutCache()
            deleteCache.hasObservers() shouldBe true

            session.replace("replacement-token").test().assertComplete()
            initialBuild.hasObservers() shouldBe false
            deleteCache.hasObservers() shouldBe false
            presenter.rebuild()

            verify(exactly = 0) { api.retryBuild(any(), any(), any(), any()) }
        }

        it("should block duplicate submissions, report retry failure, and allow another rebuild") {
            presenter.rebuild()
            presenter.rebuild()
            presenter.rebuildWithoutCache()

            verify(exactly = 1) { api.retryBuild(any(), any(), any(), any()) }
            verify(exactly = 0) { api.deleteCache(any(), any(), any()) }

            retry.onError(IllegalStateException("Retry failed"))
            presenter.rebuildFailed.value shouldBe true

            presenter.rebuildWithoutCache()
            presenter.rebuildFailed.value shouldBe false

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

        it("should report cache deletion failure and allow another rebuild") {
            presenter.rebuildWithoutCache()
            deleteCache.onError(IllegalStateException("Cache deletion failed"))
            presenter.rebuildFailed.value shouldBe true
            verify(exactly = 0) { api.retryBuild(any(), any(), any(), any()) }

            presenter.rebuild()
            presenter.rebuildFailed.value shouldBe false

            verify(exactly = 1) { api.retryBuild(any(), any(), any(), any()) }
        }

        it("should report retry failure after cache deletion succeeds") {
            presenter.rebuildWithoutCache()
            deleteCache.onNext(Unit)
            presenter.rebuildFailed.value shouldBe false

            retry.onError(IllegalStateException("Retry failed"))
            presenter.rebuildFailed.value shouldBe true

            retry = PublishSubject.create()
            presenter.rebuild()
            presenter.rebuildFailed.value shouldBe false
            verify(exactly = 2) { api.retryBuild(any(), any(), any(), any()) }
        }

        listOf(false, true).forEach { fails ->
            val outcome = if (fails) "failure" else "success"
            it("blocks both actions after reopening a build until cache-free rebuild $outcome") {
                every { api.getBuild("github", "owner", "repo", 124) } returns Observable.never()
                every { api.retryBuild("github", "owner", "repo", 124) } returns Observable.empty()

                presenter.rebuildWithoutCache()
                presenter.dropView(view)
                val reopened = createPresenter()
                val reopenedScope = reopened.attachToTestScope(mockk(relaxUnitFun = true))
                val other = createPresenter(124)
                val otherScope = other.attachToTestScope(mockk(relaxUnitFun = true))
                try {
                    reopened.rebuild()
                    reopened.rebuildWithoutCache()
                    other.rebuild()
                    verify(exactly = 1) { api.deleteCache(any(), any(), any()) }
                    verify(exactly = 0) { api.retryBuild("github", "owner", "repo", 123) }
                    verify(exactly = 1) { api.retryBuild("github", "owner", "repo", 124) }

                    if (fails) {
                        deleteCache.onError(IllegalStateException("Cache deletion failed"))
                    } else {
                        deleteCache.onNext(Unit)
                        reopened.rebuild()
                        reopened.rebuildWithoutCache()
                        verify(exactly = 1) { api.retryBuild("github", "owner", "repo", 123) }
                        verify(exactly = 1) { api.deleteCache(any(), any(), any()) }
                        retry.onNext(mockk())
                        retry.onComplete()
                    }

                    verify { activity wasNot Called }
                    reopened.rebuild()
                    verify(exactly = if (fails) 1 else 2) { api.retryBuild("github", "owner", "repo", 123) }
                } finally {
                    otherScope.destroy()
                    reopenedScope.destroy()
                }
            }
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

private fun bindingWithContainer(container: FrameLayout): ActivityMainBinding {
    val child = mockk<View> {
        every { findViewById<View>(R.id.containerView) } returns container
        every { findViewById<View>(R.id.navigationView) } returns mockk<NavigationView>()
        every { findViewById<View>(R.id.toolbar) } returns mockk<Toolbar>()
    }
    val drawer = mockk<DrawerLayout> {
        every { childCount } returns 1
        every { getChildAt(0) } returns child
    }
    return ActivityMainBinding.bind(drawer)
}

class BuildPresenterWorkflowTest : DescribeSpec({
    lateinit var activity: MainActivity
    lateinit var flow: Flow
    lateinit var presenter: BuildPresenter
    lateinit var builds: PublishSubject<Build>
    lateinit var scope: MortarScope

    fun loadBuild(id: String?) {
        val workflow = mockk<Workflow> {
            every { workflowId } returns id
            every { workflowName } returns "Build and test"
        }
        val build = pollResult(step("https://example.com/output"), "finished")
        every { build.workflows } returns workflow
        every { build.vcsUrl } returns "https://github.com/owner/repo"
        every { build.username } returns "owner"
        every { build.reponame } returns "repo"
        builds.onNext(build)
    }

    beforeEach {
        RxJavaPlugins.setIoSchedulerHandler { Schedulers.trampoline() }
        RxAndroidPlugins.setInitMainThreadSchedulerHandler { Schedulers.trampoline() }
        RxAndroidPlugins.setMainThreadSchedulerHandler { Schedulers.trampoline() }

        activity = mockk(relaxUnitFun = true)
        flow = mockk(relaxed = true)
        mockkStatic(Flow::class)
        every { Flow.get(activity) } returns flow

        builds = PublishSubject.create()
        val api = mockk<CircleCIAPIClientV1_1>()
        every { api.getBuild("github", "owner", "repo", 123) } returns builds
        presenter = BuildPresenter().apply {
            di = DI {
                bind<BuildId>() with singleton { BuildId("github", "owner", "repo", 123) }
                bind<MainActivity>() with singleton { activity }
                bind<SessionService>() with singleton { testSession(api) }
            }
        }
        scope = presenter.attachToTestScope(mockk(relaxUnitFun = true))
    }

    afterEach {
        scope.destroy()
        unmockkStatic(Flow::class)
        RxAndroidPlugins.reset()
        RxJavaPlugins.reset()
    }

    it("hides workflow navigation before a build loads") {
        presenter.hasWorkflow() shouldBe false
        presenter.goToWorkflowScreen()

        verify(exactly = 0) { flow.set(any()) }
    }

    mapOf("null" to null, "empty" to "", "whitespace-only" to " \t\n").forEach { (label, id) ->
        it("hides workflow navigation and ignores selection for a $label ID") {
            loadBuild(id)

            presenter.hasWorkflow() shouldBe false
            presenter.goToWorkflowScreen()

            verify(exactly = 0) { flow.set(any()) }
            verify(exactly = 0) { activity.invalidateOptionsMenu() }
        }
    }

    it("opens the workflow screen with the original ID when it is nonblank") {
        loadBuild(" target ")
        val screen = slot<WorkflowScreen>()

        presenter.hasWorkflow() shouldBe true
        presenter.goToWorkflowScreen()

        verify(exactly = 1) { flow.set(capture(screen)) }
        verify(exactly = 1) { activity.invalidateOptionsMenu() }
        screen.captured.workflowId shouldBe " target "
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
        every { api.getBuild("github", "owner", "repo", 42) } returnsMany listOf(
            Observable.just(builds[0]),
            Observable.error(IllegalStateException("Refresh failed")),
            Observable.just(builds[1]),
            Observable.just(builds[2])
        )

        val activity = mockk<MainActivity>()
        val presenter = BuildPresenter().apply {
            di = DI {
                bind<BuildId>() with singleton { buildId }
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

            val loadedBuilds = mutableListOf<Build>()
            val view = mockk<BuildView>()
            every { view.showBuild(any()) } answers { loadedBuilds.add(firstArg()); Unit }
            scope = presenter.attachToTestScope(view)
            snapshots.assertValues(emptyList(), listOf(first))

            scheduler.advanceTimeBy(BuildPresenter.BUILD_POLLING_SECONDS, TimeUnit.SECONDS)
            verify(exactly = 2) { api.getBuild("github", "owner", "repo", 42) }
            presenter.buildLoadFailed.value shouldBe false
            snapshots.assertValues(emptyList(), listOf(first))

            scheduler.advanceTimeBy(BuildPresenter.BUILD_POLLING_SECONDS, TimeUnit.SECONDS)
            verify(exactly = 3) { api.getBuild("github", "owner", "repo", 42) }
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
    every { workflows } returns null
}
