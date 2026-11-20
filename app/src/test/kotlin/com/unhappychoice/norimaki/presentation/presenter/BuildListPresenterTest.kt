package com.unhappychoice.norimaki.presentation.presenter

import com.github.unhappychoice.circleci.CircleCIAPIClientV1_1
import com.github.unhappychoice.circleci.v1.response.Build
import com.unhappychoice.norimaki.MainActivity
import com.unhappychoice.norimaki.domain.model.BuildId
import com.unhappychoice.norimaki.domain.service.SessionService
import com.unhappychoice.norimaki.presentation.screen.BuildScreen
import com.unhappychoice.norimaki.presentation.view.BuildListView
import flow.Flow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
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
import java.util.Date
import java.util.concurrent.TimeUnit
import mortar.MortarScope
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.singleton

class BuildListPresenterTest : DescribeSpec({
    val activity = mockk<MainActivity>()
    lateinit var scope: MortarScope

    lateinit var scheduler: TestScheduler

    beforeEach {
        scheduler = TestScheduler()
        RxJavaPlugins.setComputationSchedulerHandler { scheduler }
        RxJavaPlugins.setIoSchedulerHandler { Schedulers.trampoline() }
        RxAndroidPlugins.setInitMainThreadSchedulerHandler { Schedulers.trampoline() }
        RxAndroidPlugins.setMainThreadSchedulerHandler { Schedulers.trampoline() }
    }

    afterEach { scope.destroy() }

    afterSpec {
        RxJavaPlugins.reset()
        RxAndroidPlugins.reset()
    }

    fun presenter(
        api: CircleCIAPIClientV1_1,
        session: SessionService = testSession(api)
    ) = BuildListPresenter().apply {
        di = DI {
            bind<MainActivity>() with singleton { activity }
            bind<SessionService>() with singleton { session }
            bind<String>() with singleton { "github/owner/repo" }
        }
        scope = attachToTestScope(mockk<BuildListView>())
    }

    fun build(number: Int, state: String = "finished"): Build = mockk {
        every { vcsUrl } returns "https://github.com/owner/repo"
        every { username } returns "owner"
        every { reponame } returns "repo"
        every { buildNum } returns number
        every { queuedAt } returns Date(number.toLong())
        every { lifecycle } returns state
    }

    fun CircleCIAPIClientV1_1.pageAt(offset: Int) =
        getProjectBuilds(vcsType = "github", userName = "owner", project = "repo", offset = offset, limit = 20)

    describe("build navigation") {
        it("opens a build only when it has an identifier and an active session") {
            val api = mockk<CircleCIAPIClientV1_1>()
            every { api.pageAt(0) } returns Observable.just(emptyList())
            val session = testSession(api)
            val presenter = presenter(api, session = session)
            val valid = build(42)
            val incomplete = listOf(
                build(43).apply { every { username } returns null },
                build(44).apply { every { reponame } returns null },
                build(45).apply { every { buildNum } returns null }
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

    describe("project build polling") {
        it("does not fetch build details when the session ends before subscription") {
            val api = mockk<CircleCIAPIClientV1_1>()
            val session = testSession(api)
            val running = build(1, state = "running")
            every { api.pageAt(0) } returns Observable.just(listOf(running))
            val presenter = presenter(api, session = session)
            scheduler.triggerActions()
            presenter.builds.take(1).test().assertResult(listOf(running))

            val details = presenter.getBuildDetails(running)
            session.replace("replacement-token").test().assertComplete()

            details.test().assertResult()
            verify(exactly = 0) { api.getBuild("github", "owner", "repo", 1) }
        }

        it("updates a paginated build from its details without advancing pagination") {
            val api = mockk<CircleCIAPIClientV1_1>()
            val newest = build(3)
            val running = build(1, state = "running")
            val finished = build(1)
            every { api.pageAt(0) } returns Observable.just(listOf(newest))
            every { api.pageAt(20) } returns Observable.just(listOf(running))
            every { api.getBuild("github", "owner", "repo", 1) } returns Observable.just(finished)
            val presenter = presenter(api)
            val builds = presenter.builds.test()

            scheduler.triggerActions()
            presenter.getBuilds()
            scheduler.triggerActions()
            builds.assertValues(emptyList(), listOf(newest), listOf(newest, running))

            scheduler.advanceTimeBy(BuildListPresenter.BUILD_LIST_POLLING_SECONDS, TimeUnit.SECONDS)
            builds.assertValueAt(3, listOf(newest, finished))
            scheduler.advanceTimeBy(BuildListPresenter.BUILD_LIST_POLLING_SECONDS, TimeUnit.SECONDS)

            builds.assertValueCount(4)
            verify(exactly = 1) { api.getBuild("github", "owner", "repo", 1) }
            presenter.page.value shouldBe 2
            presenter.hasMore.value shouldBe true
            builds.dispose()
        }

        it("stops at the first unchanged project page and displays a new build once") {
            val api = mockk<CircleCIAPIClientV1_1>()
            val added = build(200)
            var projectBuilds = (199 downTo 1).map { build(it) }
            val offsets = mutableListOf<Int>()
            every { api.pageAt(any()) } answers {
                val offset = arg<Int>(3)
                offsets.add(offset)
                Observable.just(projectBuilds.drop(offset).take(BUILD_LIST_PAGE_SIZE))
            }
            val builds = presenter(api).builds.test()
            scheduler.triggerActions()
            offsets shouldBe listOf(0) + (0 until 200 step BUILD_LIST_PAGE_SIZE).toList()
            builds.values().last() shouldBe projectBuilds

            offsets.clear()
            scheduler.advanceTimeBy(BuildListPresenter.BUILD_LIST_POLLING_SECONDS, TimeUnit.SECONDS)
            offsets shouldBe listOf(0)
            builds.values().last() shouldBe projectBuilds

            projectBuilds = listOf(added) + projectBuilds
            offsets.clear()
            scheduler.advanceTimeBy(2 * BuildListPresenter.BUILD_LIST_POLLING_SECONDS, TimeUnit.SECONDS)
            offsets shouldBe listOf(0, 0)
            builds.values().count { it == projectBuilds } shouldBe 1
            builds.dispose()
        }

        it("recovers builds beyond the first page after pagination ends") {
            val api = mockk<CircleCIAPIClientV1_1>()
            val known = build(1)
            val missed = build(2)
            val firstPage = (22 downTo 3).map { build(it) }
            every { api.pageAt(0) } returnsMany listOf(
                Observable.just(listOf(known)),
                Observable.just(listOf(known)),
                Observable.just(firstPage)
            )
            every { api.pageAt(20) } returnsMany listOf(
                Observable.just(emptyList()),
                Observable.just(listOf(missed, known))
            )
            val presenter = presenter(api)
            val builds = presenter.builds.test()
            presenter.getBuilds()
            presenter.isLoading.value shouldBe true
            presenter.getBuilds()
            scheduler.triggerActions()
            verify(exactly = 2) { api.pageAt(0) }
            presenter.page.value shouldBe 1
            presenter.getBuilds()
            scheduler.triggerActions()
            presenter.hasMore.value shouldBe false

            scheduler.advanceTimeBy(BuildListPresenter.BUILD_LIST_POLLING_SECONDS, TimeUnit.SECONDS)

            builds.assertValues(emptyList(), listOf(known), firstPage + listOf(missed, known))
            verify(exactly = 2) { api.pageAt(20) }
            verify(exactly = 0) { api.pageAt(40) }
            presenter.page.value shouldBe 2
            presenter.hasMore.value shouldBe false
            builds.dispose()
        }
    }

    describe("project build pagination") {
        it("merges pages and stops requesting builds after an empty page") {
            val api = mockk<CircleCIAPIClientV1_1>()
            val latest = build(2)
            val older = build(1)
            every { api.pageAt(0) } returns Observable.just(listOf(latest))
            every { api.pageAt(20) } returns Observable.just(listOf(older))
            every { api.pageAt(40) } returns Observable.just(emptyList())
            val presenter = presenter(api)
            val builds = presenter.builds.test()

            scheduler.triggerActions()
            repeat(3) {
                presenter.getBuilds()
                scheduler.triggerActions()
            }

            builds.values().last().toSet() shouldBe setOf(latest, older)
            presenter.hasMore.value shouldBe false
            verify(exactly = 0) { api.pageAt(60) }
        }

        it("clears loaded builds and discards queued updates when the session changes") {
            val api = mockk<CircleCIAPIClientV1_1>()
            val session = testSession(api)
            val first = build(2)
            every { api.pageAt(0) } returns Observable.just(listOf(first))
            every { api.pageAt(20) } returns Observable.just(listOf(build(1)))
            val presenter = presenter(api, session = session)
            val builds = presenter.builds.test()
            scheduler.triggerActions()
            builds.assertValues(emptyList(), listOf(first))

            presenter.getBuilds()
            session.replace("replacement-token").test().assertComplete()
            scheduler.triggerActions()

            builds.assertValues(emptyList(), listOf(first), emptyList())
            presenter.page.value shouldBe 0
            presenter.isLoading.value shouldBe false
        }
    }
})
