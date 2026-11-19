package com.unhappychoice.norimaki.presentation.presenter

import com.github.unhappychoice.circleci.CircleCIAPIClientV1_1
import com.github.unhappychoice.circleci.v1.response.Build
import com.github.unhappychoice.circleci.v1.response.Workflow
import com.unhappychoice.norimaki.domain.service.SessionService
import com.unhappychoice.norimaki.presentation.screen.WorkflowScreen
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
        workflowId: String? = "target",
        session: SessionService = testSession(api)
    ) = BuildListPresenter().apply {
        di = DI {
            bind<SessionService>() with singleton { session }
            bind<String>() with singleton { "github/owner/repo" }
            workflowId?.let { id ->
                bind<String>(tag = WorkflowScreen.WORKFLOW_ID_TAG) with singleton { id }
            }
        }
        scope = attachToTestScope(mockk<BuildListView>())
    }

    fun build(id: String?, number: Int, state: String = "finished"): Build {
        val workflow = id?.let { mockk<Workflow> { every { workflowId } returns it } }
        return mockk {
            every { workflows } returns workflow
            every { vcsUrl } returns "https://github.com/owner/repo"
            every { username } returns "owner"
            every { reponame } returns "repo"
            every { buildNum } returns number
            every { queuedAt } returns null
            every { lifecycle } returns state
        }
    }

    fun CircleCIAPIClientV1_1.pageAt(offset: Int) =
        getProjectBuilds(vcsType = "github", userName = "owner", project = "repo", offset = offset, limit = 20)

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

        it("updates a paginated workflow build from its details without advancing pagination") {
            val api = mockk<CircleCIAPIClientV1_1>()
            val newest = build("target", 3)
            val running = build("target", 1, state = "running")
            val finished = build(null, 1)
            every { api.pageAt(0) } returns Observable.just(listOf(newest, build("other", 2)))
            every { api.pageAt(20) } returns Observable.just(listOf(running))
            every { api.getBuild("github", "owner", "repo", 1) } returns Observable.just(finished)
            val presenter = presenter(api)
            val builds = presenter.builds.test()

            scheduler.triggerActions()
            presenter.getBuilds()
            scheduler.triggerActions()
            builds.assertValueAt(2, listOf(running, newest))

            scheduler.advanceTimeBy(BuildListPresenter.BUILD_LIST_POLLING_SECONDS, TimeUnit.SECONDS)
            builds.assertValueAt(3, listOf(finished, newest))
            scheduler.advanceTimeBy(20, TimeUnit.SECONDS)

            builds.assertValueCount(4)
            verify(exactly = 1) { api.getBuild("github", "owner", "repo", 1) }
            presenter.page.value shouldBe 2
            presenter.hasMore.value shouldBe true
            builds.dispose()
        }
    }

    describe("workflow polling") {
        it("recovers matching builds beyond the first page after pagination ends") {
            val api = mockk<CircleCIAPIClientV1_1>()
            val known = build("target", 1)
            val missed = build("target", 2)
            val firstPage = (22 downTo 3).map { build("other", it) }
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

            builds.assertValues(emptyList(), listOf(known), listOf(missed, known))
            verify(exactly = 2) { api.pageAt(20) }
            verify(exactly = 0) { api.pageAt(40) }
            presenter.page.value shouldBe 2
            presenter.hasMore.value shouldBe false
            builds.dispose()
        }
    }

    describe("workflow pagination") {
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
            val api = mockk<CircleCIAPIClientV1_1>()
            val session = testSession(api)
            val first = build("target", 2)
            every { api.pageAt(0) } returns Observable.just(listOf(first))
            every { api.pageAt(20) } returns Observable.just(listOf(build("target", 1)))
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

        it("does not auto-load pages for an unfiltered project") {
            val api = mockk<CircleCIAPIClientV1_1>()
            every { api.pageAt(0) } returns Observable.just(listOf(build("other", 3)))
            presenter(api, workflowId = null).getBuildsIfUnderfilled(canScroll = false)
            verify(exactly = 0) { api.pageAt(20) }
        }
    }
})
