package com.unhappychoice.norimaki.presentation.presenter

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.github.unhappychoice.circleci.CircleCIAPIClientV1_1
import com.github.unhappychoice.circleci.v1.response.Build
import com.github.unhappychoice.circleci.v1.response.User
import com.github.unhappychoice.circleci.v1.response.Workflow
import com.unhappychoice.norimaki.MainActivity
import com.unhappychoice.norimaki.domain.model.BuildId
import com.unhappychoice.norimaki.domain.service.SessionService
import com.unhappychoice.norimaki.presentation.screen.BuildScreen
import com.unhappychoice.norimaki.presentation.screen.WorkflowScreen
import com.unhappychoice.norimaki.presentation.view.BuildListView
import flow.Flow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import io.reactivex.Observable
import io.reactivex.android.plugins.RxAndroidPlugins
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.schedulers.Schedulers
import io.reactivex.schedulers.TestScheduler
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.SingleSubject
import mortar.MortarScope
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.singleton
import java.util.concurrent.TimeUnit

class BuildListPresenterTest : DescribeSpec({
    lateinit var scope: MortarScope

    lateinit var scheduler: TestScheduler
    lateinit var activity: MainActivity

    beforeEach {
        scheduler = TestScheduler()
        activity = mockk()
        RxJavaPlugins.setComputationSchedulerHandler { scheduler }
        RxJavaPlugins.setIoSchedulerHandler { Schedulers.trampoline() }
        RxAndroidPlugins.setInitMainThreadSchedulerHandler { Schedulers.trampoline() }
        RxAndroidPlugins.setMainThreadSchedulerHandler { scheduler }
    }

    afterEach { scope.destroy() }

    afterSpec {
        RxJavaPlugins.reset()
        RxAndroidPlugins.reset()
    }

    fun presenter(
        api: CircleCIAPIClientV1_1,
        workflowId: String? = "target",
        session: SessionService = testSession(api),
        view: BuildListView = mockk()
    ) = BuildListPresenter().apply {
        di = DI {
            bind<SessionService>() with singleton { session }
            bind<MainActivity>() with singleton { activity }
            bind<String>() with singleton { "github/owner/repo" }
            workflowId?.let { id ->
                bind<String>(tag = WorkflowScreen.WORKFLOW_ID_TAG) with singleton { id }
            }
        }
        scope = attachToTestScope(view)
    }

    fun startPolling(presenter: BuildListPresenter) {
        val owner = PollingLifecycleOwner()
        owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)
        presenter.observePolling(owner.lifecycle)
        scheduler.triggerActions()
    }

    fun build(id: String?, number: Int, state: String = "finished", result: String = state): Build {
        val workflow = id?.let { mockk<Workflow> { every { workflowId } returns it } }
        return mockk {
            every { workflows } returns workflow
            every { vcsUrl } returns "https://github.com/owner/repo"
            every { username } returns "owner"
            every { reponame } returns "repo"
            every { buildNum } returns number
            every { queuedAt } returns null
            every { lifecycle } returns state
            every { status } returns result
        }
    }

    fun CircleCIAPIClientV1_1.pageAt(offset: Int) =
        getProjectBuilds(vcsType = "github", userName = "owner", project = "repo", offset = offset, limit = 20)

    fun advanceRefresh() =
        scheduler.advanceTimeBy(BuildListPresenter.BUILD_LIST_POLLING_SECONDS, TimeUnit.SECONDS)

    describe("polling lifecycle") {
        it("pauses while stopped and resumes immediately with a single loop") {
            val owner = PollingLifecycleOwner()
            val api = mockk<CircleCIAPIClientV1_1>()
            every { api.pageAt(0) } returns Observable.just(emptyList())
            val view = mockk<BuildListView>()
            val presenter = presenter(api, view = view)
            val interval = BuildListPresenter.BUILD_LIST_POLLING_SECONDS

            owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            presenter.observePolling(owner.lifecycle)
            scheduler.advanceTimeBy(interval, TimeUnit.SECONDS)
            verify(exactly = 1) { api.pageAt(0) }

            owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)
            advanceRefresh()
            verify(exactly = 2) { api.pageAt(0) }

            owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            scheduler.advanceTimeBy(interval * 3, TimeUnit.SECONDS)
            verify(exactly = 2) { api.pageAt(0) }

            owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)
            scheduler.triggerActions()
            verify(exactly = 3) { api.pageAt(0) }
            scheduler.advanceTimeBy(interval * 2, TimeUnit.SECONDS)
            verify(exactly = 5) { api.pageAt(0) }

            presenter.dropView(view)
            owner.lifecycle.observerCount shouldBe 0
            scheduler.advanceTimeBy(interval * 2, TimeUnit.SECONDS)
            verify(exactly = 5) { api.pageAt(0) }
        }

        it("refetches older builds when polling resumes before the pending results are applied") {
            val owner = PollingLifecycleOwner()
            val api = mockk<CircleCIAPIClientV1_1>()
            val known = build("target", 1)
            val missed = build("target", 2)
            val firstPage = (22 downTo 3).map { build("other", it) }
            every { api.pageAt(0) } returnsMany listOf(
                Observable.just(listOf(known)),
                Observable.just(firstPage)
            )
            every { api.pageAt(20) } returns Observable.just(listOf(missed, known))
            val mergeScheduler = TestScheduler()
            RxJavaPlugins.setComputationSchedulerHandler { mergeScheduler }
            val presenter = presenter(api)
            RxJavaPlugins.setComputationSchedulerHandler { scheduler }
            val builds = presenter.builds.test()
            scheduler.triggerActions()
            mergeScheduler.triggerActions()
            scheduler.triggerActions()

            owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)
            presenter.observePolling(owner.lifecycle)
            advanceRefresh()
            verify(exactly = 1) { api.pageAt(20) }
            builds.assertValues(emptyList(), listOf(known))

            owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)
            scheduler.triggerActions()
            verify(exactly = 2) { api.pageAt(20) }

            mergeScheduler.triggerActions()
            scheduler.triggerActions()
            mergeScheduler.triggerActions()
            scheduler.triggerActions()
            builds.assertValues(emptyList(), listOf(known), listOf(missed, known))
            builds.dispose()
        }

        it("cancels an in-flight poll when stopped") {
            val owner = PollingLifecycleOwner()
            val api = mockk<CircleCIAPIClientV1_1>()
            val inFlight = PublishSubject.create<List<Build>>()
            every { api.pageAt(0) } returnsMany listOf(
                Observable.just(emptyList()),
                inFlight
            )
            val presenter = presenter(api)
            scheduler.triggerActions()

            owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)
            presenter.observePolling(owner.lifecycle)
            advanceRefresh()
            inFlight.hasObservers() shouldBe true

            owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            inFlight.hasObservers() shouldBe false
        }
    }

    describe("initial build load") {
        it("starts polling after authentication finishes while the view is started") {
            val api = mockk<CircleCIAPIClientV1_1>()
            val authentication = SingleSubject.create<User>()
            val session = SessionService({ "test-token" }, {}, { api }, { authentication })
            every { api.pageAt(0) } returns Observable.just(emptyList())
            val presenter = presenter(api, session = session)
            startPolling(presenter)
            advanceRefresh()
            verify(exactly = 0) { api.pageAt(0) }

            authentication.onSuccess(mockk())
            scheduler.triggerActions()
            verify(exactly = 1) { api.pageAt(0) }
            advanceRefresh()
            verify(exactly = 2) { api.pageAt(0) }
        }

        it("cancels a poll retrying page zero and retries again on resume") {
            val owner = PollingLifecycleOwner()
            val api = mockk<CircleCIAPIClientV1_1>()
            val retry = PublishSubject.create<List<Build>>()
            val build = build("target", 1)
            every { api.pageAt(0) } returnsMany listOf(
                Observable.error(IllegalStateException("Initial page unavailable")),
                retry,
                Observable.just(listOf(build))
            )
            val presenter = presenter(api)
            scheduler.triggerActions()
            presenter.canRetryPage.value shouldBe true
            owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)
            presenter.observePolling(owner.lifecycle)
            advanceRefresh()
            retry.hasObservers() shouldBe true

            owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            retry.hasObservers() shouldBe false
            presenter.isLoading.value shouldBe false
            advanceRefresh()
            verify(exactly = 2) { api.pageAt(0) }

            owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)
            scheduler.triggerActions()
            presenter.builds.take(1).test().assertResult(listOf(build))
            presenter.page.value shouldBe 1
            presenter.canRetryPage.value shouldBe false
        }

        it("shares a delayed full page with polling even when workflow filtering hides it") {
            val api = mockk<CircleCIAPIClientV1_1>()
            val initialResponse = PublishSubject.create<List<Build>>()
            val firstPage = (40 downTo 21).map { build("other", it) }
            val olderPage = (20 downTo 1).map { build("other", it) }
            every { api.pageAt(0) } returnsMany listOf(initialResponse, Observable.just(firstPage))
            every { api.pageAt(20) } returns Observable.just(olderPage)
            every { api.pageAt(40) } returns Observable.just(emptyList())
            val presenter = presenter(api)
            startPolling(presenter)
            advanceRefresh()
            verify(exactly = 1) { api.pageAt(0) }
            initialResponse.onNext(firstPage)
            initialResponse.onComplete()
            scheduler.triggerActions()
            presenter.page.value shouldBe 1
            scheduler.advanceTimeBy(BuildListPresenter.BUILD_LIST_POLLING_SECONDS - 1, TimeUnit.SECONDS)
            verify(exactly = 1) { api.pageAt(0) }

            scheduler.advanceTimeBy(1, TimeUnit.SECONDS)
            verify(exactly = 2) { api.pageAt(0) }
            verify(exactly = 0) { api.pageAt(20) }
            verify(exactly = 0) { api.pageAt(40) }

            presenter.getBuilds()
            scheduler.triggerActions()
            verify(exactly = 1) { api.pageAt(20) }
            presenter.page.value shouldBe 2
        }

        it("shares an in-flight manual retry of page zero with polling") {
            val api = mockk<CircleCIAPIClientV1_1>()
            val retry = PublishSubject.create<List<Build>>()
            val known = build("target", 1)
            every { api.pageAt(0) } returnsMany listOf(
                Observable.error(IllegalStateException("Initial page unavailable")),
                retry,
                Observable.just(listOf(known))
            )
            val presenter = presenter(api)
            scheduler.triggerActions()
            presenter.canRetryPage.value shouldBe true

            presenter.getBuilds()
            scheduler.triggerActions()
            presenter.canRetryPage.value shouldBe false
            presenter.isLoading.value shouldBe true
            startPolling(presenter)
            advanceRefresh()
            verify(exactly = 2) { api.pageAt(0) }

            retry.onNext(listOf(known))
            retry.onComplete()
            scheduler.triggerActions()
            presenter.page.value shouldBe 1
            presenter.isLoading.value shouldBe false
            advanceRefresh()
            verify(exactly = 3) { api.pageAt(0) }
        }

        it("retries page zero after an initial failure and resumes refreshing") {
            val api = mockk<CircleCIAPIClientV1_1>()
            val known = build("target", 1)
            val latest = build("target", 2)
            every { api.pageAt(0) } returnsMany listOf(
                Observable.error(IllegalStateException("Initial page unavailable")),
                Observable.just(listOf(known)),
                Observable.just(listOf(latest, known))
            )
            val presenter = presenter(api)
            val builds = presenter.builds.test()
            scheduler.triggerActions()
            presenter.isLoading.value shouldBe false
            presenter.page.value shouldBe 0
            presenter.canRetryPage.value shouldBe true
            verify(exactly = 1) { api.pageAt(0) }

            startPolling(presenter)
            advanceRefresh()
            presenter.page.value shouldBe 1
            presenter.canRetryPage.value shouldBe false
            builds.assertValues(emptyList(), listOf(known))
            advanceRefresh()
            builds.assertValues(emptyList(), listOf(known), listOf(latest, known))
            verify(exactly = 3) { api.pageAt(0) }
            verify(exactly = 0) { api.pageAt(20) }
            builds.dispose()
        }
    }

    describe("build navigation") {
        it("opens a build only when it has an identifier and an active session") {
            val api = mockk<CircleCIAPIClientV1_1>()
            every { api.pageAt(0) } returns Observable.just(emptyList())
            val session = testSession(api)
            val presenter = presenter(api, session = session)
            val valid = build("target", 42)
            val incomplete = listOf(
                build("target", 43).apply { every { username } returns null },
                build("target", 44).apply { every { reponame } returns null },
                build("target", 45).apply { every { buildNum } returns null }
            )
            val flow = mockk<Flow>(relaxed = true)

            mockkStatic(Flow::class)
            try {
                every { Flow.get(activity) } returns flow
                incomplete.forEach(presenter::goToBuildView)
                presenter.goToBuildView(valid)
                session.replace("replacement-token").test().assertComplete()
                presenter.goToBuildView(valid)

                verify(exactly = 1) { flow.set(any()) }
                verify { flow.set(BuildScreen(BuildId("github", "owner", "repo", 42))) }
            } finally {
                unmockkStatic(Flow::class)
            }
        }
    }

    describe("build list polling") {
        it("does not fetch build details when the session ends before subscription") {
            val api = mockk<CircleCIAPIClientV1_1>()
            val session = testSession(api)
            val running = build("target", 1, state = "running")
            every { api.pageAt(0) } returns Observable.just(listOf(running))
            val presenter = presenter(api, session = session)
            scheduler.triggerActions()
            presenter.builds.take(1).test().assertResult(listOf(running))

            val details = presenter.getBuildDetails(running)
            session.replace("replacement-token").test().assertComplete()

            details.test().assertResult()
            verify(exactly = 0) { api.getBuild("github", "owner", "repo", 1) }
        }

        it("publishes ten changed build statuses in one history update when the latest page is unchanged") {
            val api = mockk<CircleCIAPIClientV1_1>()
            val latest = listOf(build("target", 11))
            val running = (1..10).map { build("target", it, state = "running") }
            val finished = (1..10).map { number ->
                build("target", number, result = "success").also { response ->
                    every { api.getBuild("github", "owner", "repo", number) } returns Observable.just(response)
                }
            }
            every { api.pageAt(0) } returns Observable.just(latest)
            every { api.pageAt(20) } returns Observable.just(running)
            val presenter = presenter(api)
            val builds = presenter.builds.test()

            try {
                scheduler.triggerActions()
                presenter.getBuilds()
                scheduler.triggerActions()

                startPolling(presenter)
                advanceRefresh()

                builds.assertValues(emptyList(), latest, running + latest, finished + latest)
                builds.values().last().take(10).map { it.status } shouldBe List(10) { "success" }
            } finally {
                builds.dispose()
            }
        }

        it("keeps refreshed workflow build details when an older page arrives late") {
            val api = mockk<CircleCIAPIClientV1_1>()
            val newest = build("target", 3)
            val running = build("target", 1, state = "running")
            val finished = build(null, 1)
            val unseen = build("target", 0)
            val latePage = PublishSubject.create<List<Build>>()
            every { api.pageAt(0) } returns Observable.just(listOf(newest, build("other", 2)))
            every { api.pageAt(20) } returns Observable.just(listOf(running))
            every { api.pageAt(40) } returns latePage
            every { api.getBuild("github", "owner", "repo", 1) } returns Observable.just(finished)
            val presenter = presenter(api)
            val builds = presenter.builds.test()

            try {
                scheduler.triggerActions()
                presenter.getBuilds()
                scheduler.triggerActions()
                builds.assertValues(emptyList(), listOf(newest), listOf(running, newest))
                presenter.getBuilds()
                scheduler.triggerActions()
                verify(exactly = 1) { api.pageAt(40) }

                startPolling(presenter)
                advanceRefresh()
                builds.assertValueAt(3, listOf(finished, newest))
                advanceRefresh()

                builds.assertValueCount(4)
                verify(exactly = 1) { api.getBuild("github", "owner", "repo", 1) }
                presenter.page.value shouldBe 2
                presenter.hasMore.value shouldBe true

                latePage.onNext(listOf(running, unseen))
                latePage.onComplete()
                scheduler.triggerActions()
                builds.assertValueAt(4, listOf(unseen, finished, newest))
                presenter.page.value shouldBe 3
            } finally {
                builds.dispose()
            }
        }
    }

    describe("workflow polling") {
        it("uses the latest project page IDs without reading older build IDs") {
            val api = mockk<CircleCIAPIClientV1_1>()
            val known = build("target", 21)
            val added = build("target", 41)
            val older = build("target", 1)
            var projectBuilds = (40 downTo 22).map { build("other", it) } + known +
                (20 downTo 2).map { build("other", it) } + older
            val offsets = mutableListOf<Int>()
            every { api.pageAt(any()) } answers {
                val offset = arg<Int>(3)
                offsets.add(offset)
                Observable.just(projectBuilds.drop(offset).take(BUILD_LIST_PAGE_SIZE))
            }
            val presenter = presenter(api)
            val builds = presenter.builds.test()
            scheduler.triggerActions()
            startPolling(presenter)
            offsets shouldBe listOf(0)
            builds.assertValues(emptyList(), listOf(known))

            presenter.getBuilds()
            scheduler.triggerActions()
            clearMocks(older, answers = false)
            offsets.clear()
            scheduler.advanceTimeBy(BuildListPresenter.BUILD_LIST_POLLING_SECONDS, TimeUnit.SECONDS)
            offsets shouldBe listOf(0)
            builds.assertValues(emptyList(), listOf(known), listOf(older, known))
            verify(exactly = 0) { older.buildNum }

            projectBuilds = listOf(added) + projectBuilds
            offsets.clear()
            scheduler.advanceTimeBy(2 * BuildListPresenter.BUILD_LIST_POLLING_SECONDS, TimeUnit.SECONDS)
            offsets shouldBe listOf(0, 0)
            builds.assertValues(emptyList(), listOf(known), listOf(older, known), listOf(added, older, known))
            builds.dispose()
        }

        it("shows fetched workflow builds and fills the gap when polling resumes") {
            val api = mockk<CircleCIAPIClientV1_1>()
            val known = build("target", 1)
            val missed = build("target", 2)
            val newest = build("target", 22)
            val firstPage = listOf(newest) + (21 downTo 3).map { build("other", it) }
            every { api.pageAt(0) } returnsMany listOf(
                Observable.just(listOf(known)),
                Observable.just(firstPage),
                Observable.just(firstPage)
            )
            every { api.pageAt(20) } returnsMany listOf(
                Observable.just(emptyList()),
                Observable.error(IllegalStateException("Backfill request failed")),
                Observable.just(listOf(missed, known))
            )
            val presenter = presenter(api)
            val builds = presenter.builds.test()
            presenter.isLoading.value shouldBe true
            presenter.getBuilds()
            scheduler.triggerActions()
            verify(exactly = 1) { api.pageAt(0) }
            presenter.page.value shouldBe 1
            presenter.getBuilds()
            scheduler.triggerActions()
            presenter.hasMore.value shouldBe false

            val owner = PollingLifecycleOwner()
            owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)
            presenter.observePolling(owner.lifecycle)
            advanceRefresh()

            builds.assertValues(emptyList(), listOf(known), listOf(newest, known))

            owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)
            scheduler.triggerActions()

            builds.assertValues(emptyList(), listOf(known), listOf(newest, known), listOf(newest, missed, known))
            verify(exactly = 3) { api.pageAt(20) }
            verify(exactly = 0) { api.pageAt(40) }
            presenter.page.value shouldBe 2
            presenter.hasMore.value shouldBe false
            builds.dispose()
        }
    }

    describe("workflow pagination") {
        it("retries a failed page from an empty workflow list at the same offset") {
            val api = mockk<CircleCIAPIClientV1_1>()
            val firstPage = (21 downTo 2).map { build("other", it) }
            val matchingJob = build("target", 1)
            every { api.pageAt(0) } returns Observable.just(firstPage)
            every { api.pageAt(20) } returnsMany listOf(
                Observable.error(IllegalStateException("Page request failed")),
                Observable.just(listOf(matchingJob))
            )
            val presenter = presenter(api)
            val builds = presenter.builds.test()
            scheduler.triggerActions()

            presenter.getBuildsIfUnderfilled(canScroll = false)
            scheduler.triggerActions()
            presenter.canRetryPage.value shouldBe true
            presenter.isLoading.value shouldBe false
            presenter.hasMore.value shouldBe true
            presenter.page.value shouldBe 1
            builds.assertValue(emptyList())

            presenter.getBuildsIfUnderfilled(canScroll = false)
            scheduler.advanceTimeBy(BuildListPresenter.BUILD_LIST_POLLING_SECONDS, TimeUnit.SECONDS)
            verify(exactly = 1) { api.pageAt(20) }

            presenter.getBuilds()
            scheduler.triggerActions()
            presenter.canRetryPage.value shouldBe false
            presenter.page.value shouldBe 2
            builds.assertValues(emptyList(), listOf(matchingJob))
            verify(exactly = 2) { api.pageAt(20) }
            verify(exactly = 0) { api.pageAt(40) }
            builds.dispose()
        }

        it("loads through filtered-out pages and stops after an empty API page") {
            val api = mockk<CircleCIAPIClientV1_1>()
            val matchingJob = build("target", 1)
            every { api.pageAt(0) } returns Observable.just(listOf(build("other", 3)))
            every { api.pageAt(20) } returns Observable.just(listOf(build("other", 2)))
            every { api.pageAt(40) } returns Observable.just(listOf(matchingJob))
            every { api.pageAt(60) } returns Observable.just(listOf(matchingJob))
            every { api.pageAt(80) } returns Observable.just(emptyList())
            val presenter = presenter(api)
            val builds = presenter.builds.test()

            scheduler.triggerActions()
            builds.assertValue(emptyList())
            presenter.getBuildsIfUnderfilled(canScroll = false)
            scheduler.triggerActions()
            builds.assertValue(emptyList())
            presenter.getBuildsIfUnderfilled(canScroll = false)
            scheduler.triggerActions()
            builds.assertValues(emptyList(), listOf(matchingJob))

            presenter.getBuildsIfUnderfilled(canScroll = true)
            verify(exactly = 0) { api.pageAt(60) }
            presenter.getBuildsIfUnderfilled(canScroll = false)
            scheduler.triggerActions()
            presenter.hasMore.value shouldBe true
            presenter.page.value shouldBe 4
            presenter.getBuildsIfUnderfilled(canScroll = false)
            scheduler.triggerActions()
            presenter.hasMore.value shouldBe false
            presenter.getBuildsIfUnderfilled(canScroll = false)
            scheduler.triggerActions()
            verify(exactly = 0) { api.pageAt(100) }
            builds.assertValues(emptyList(), listOf(matchingJob))
        }

        it("clears loaded builds and discards queued updates when the session changes") {
            val computation = TestScheduler()
            RxJavaPlugins.setComputationSchedulerHandler { computation }
            val api = mockk<CircleCIAPIClientV1_1>()
            val session = testSession(api)
            val first = build("target", 2)
            every { api.pageAt(0) } returns Observable.just(listOf(first))
            every { api.pageAt(20) } returns Observable.just(listOf(build("target", 1)))
            val presenter = presenter(api, session = session)
            val builds = presenter.builds.test()
            scheduler.triggerActions()
            computation.triggerActions()
            scheduler.triggerActions()
            builds.assertValues(emptyList(), listOf(first))

            presenter.getBuilds()
            scheduler.triggerActions()
            session.replace("replacement-token").test().assertComplete()
            computation.triggerActions()
            scheduler.triggerActions()

            builds.assertValues(emptyList(), listOf(first), emptyList())
            presenter.page.value shouldBe 0
            presenter.isLoading.value shouldBe false
            presenter.hasMore.value shouldBe true
            presenter.getBuilds()
            scheduler.advanceTimeBy(BuildListPresenter.BUILD_LIST_POLLING_SECONDS, TimeUnit.SECONDS)
            verify(exactly = 1) { api.pageAt(0) }
            verify(exactly = 1) { api.pageAt(20) }
        }

        it("does not auto-load pages for an unfiltered project") {
            val api = mockk<CircleCIAPIClientV1_1>()
            every { api.pageAt(0) } returns Observable.just(listOf(build("other", 3)))
            presenter(api, workflowId = null).getBuildsIfUnderfilled(canScroll = false)
            verify(exactly = 0) { api.pageAt(20) }
        }
    }
})

private class PollingLifecycleOwner : LifecycleOwner {
    override val lifecycle = LifecycleRegistry.createUnsafe(this)
}
