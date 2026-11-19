package com.unhappychoice.norimaki.presentation.presenter

import com.github.unhappychoice.circleci.CircleCIAPIClientV1_1
import com.github.unhappychoice.circleci.v1.response.Build
import com.unhappychoice.norimaki.domain.service.SessionService
import com.unhappychoice.norimaki.presentation.view.BuildListView
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
import mortar.MortarScope
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.singleton
import java.util.Date
import java.util.concurrent.TimeUnit

class BuildListPresenterTest : DescribeSpec({
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

    describe("project build polling") {
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
