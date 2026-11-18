package com.unhappychoice.norimaki.presentation.presenter

import com.github.unhappychoice.circleci.CircleCIAPIClientV1_1
import com.github.unhappychoice.circleci.v1.response.Build
import com.github.unhappychoice.circleci.v1.response.Workflow
import com.unhappychoice.norimaki.presentation.screen.WorkflowScreen
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.reactivex.Observable
import io.reactivex.android.plugins.RxAndroidPlugins
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.schedulers.Schedulers
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.singleton

class BuildListPresenterTest : DescribeSpec({
    beforeSpec {
        RxJavaPlugins.setComputationSchedulerHandler { Schedulers.trampoline() }
        RxJavaPlugins.setIoSchedulerHandler { Schedulers.trampoline() }
        RxAndroidPlugins.setInitMainThreadSchedulerHandler { Schedulers.trampoline() }
        RxAndroidPlugins.setMainThreadSchedulerHandler { Schedulers.trampoline() }
    }

    afterSpec {
        RxJavaPlugins.reset()
        RxAndroidPlugins.reset()
    }

    fun presenter(api: CircleCIAPIClientV1_1, workflowId: String? = "target") = BuildListPresenter().apply {
        di = DI {
            bind<CircleCIAPIClientV1_1>() with singleton { api }
            bind<String>() with singleton { "github/owner/repo" }
            workflowId?.let { id ->
                bind<String>(tag = WorkflowScreen.WORKFLOW_ID_TAG) with singleton { id }
            }
        }
    }

    fun build(id: String, number: Int): Build {
        val workflow = mockk<Workflow> { every { workflowId } returns id }
        return mockk {
            every { workflows } returns workflow
            every { username } returns "owner"
            every { reponame } returns "repo"
            every { vcsUrl } returns "https://github.com/owner/repo"
            every { buildNum } returns number
            every { queuedAt } returns null
        }
    }

    fun CircleCIAPIClientV1_1.pageAt(offset: Int) =
        getProjectBuilds(vcsType = "github", userName = "owner", project = "repo", offset = offset, limit = 20)

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

            presenter.getBuilds()
            builds.assertValue(emptyList())
            presenter.getBuildsIfUnderfilled(canScroll = false)
            builds.assertValue(emptyList())
            presenter.getBuildsIfUnderfilled(canScroll = false)
            builds.assertValues(emptyList(), listOf(matchingJob))

            presenter.getBuildsIfUnderfilled(canScroll = true)
            verify(exactly = 0) { api.pageAt(60) }
            presenter.getBuildsIfUnderfilled(canScroll = false)
            presenter.hasMore.value shouldBe true
            presenter.page.value shouldBe 4
            presenter.getBuildsIfUnderfilled(canScroll = false)
            presenter.hasMore.value shouldBe false
            presenter.getBuildsIfUnderfilled(canScroll = false)
            verify(exactly = 0) { api.pageAt(100) }
            builds.assertValues(emptyList(), listOf(matchingJob))
        }

        it("does not auto-load pages for an unfiltered project") {
            val api = mockk<CircleCIAPIClientV1_1>()
            presenter(api, workflowId = null).getBuildsIfUnderfilled(canScroll = false)
            verify(exactly = 0) { api.pageAt(0) }
        }
    }
})
