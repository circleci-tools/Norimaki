package com.unhappychoice.norimaki.presentation.presenter

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
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
import io.reactivex.schedulers.TestScheduler
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.singleton
import java.util.concurrent.TimeUnit

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
            every { vcsUrl } returns "https://github.com/owner/repo"
            every { username } returns "owner"
            every { reponame } returns "repo"
            every { buildNum } returns number
            every { queuedAt } returns null
        }
    }

    fun CircleCIAPIClientV1_1.pageAt(offset: Int) =
        getProjectBuilds(vcsType = "github", userName = "owner", project = "repo", offset = offset, limit = 20)

    describe("polling lifecycle") {
        it("stops polling while the attached screen is stopped and resumes one loop immediately") {
            val scheduler = TestScheduler()
            val previousScheduler = RxJavaPlugins.getComputationSchedulerHandler()
            RxJavaPlugins.setComputationSchedulerHandler { scheduler }
            val owner = PollingLifecycleOwner()
            val api = mockk<CircleCIAPIClientV1_1>()
            every { api.pageAt(0) } returns Observable.just(emptyList())
            val presenter = presenter(api)
            val interval = BuildListPresenter.BUILD_LIST_POLLING_SECONDS

            try {
                owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
                presenter.observePolling(owner.lifecycle)
                scheduler.advanceTimeBy(interval, TimeUnit.SECONDS)
                verify(exactly = 0) { api.pageAt(0) }

                owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)
                scheduler.triggerActions()
                verify(exactly = 1) { api.pageAt(0) }

                owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
                scheduler.advanceTimeBy(interval * 3, TimeUnit.SECONDS)
                verify(exactly = 1) { api.pageAt(0) }

                owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)
                scheduler.triggerActions()
                verify(exactly = 2) { api.pageAt(0) }
                scheduler.advanceTimeBy(interval * 2, TimeUnit.SECONDS)
                verify(exactly = 4) { api.pageAt(0) }

                presenter.dropView(mockk())
                owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
                owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)
                scheduler.advanceTimeBy(interval * 2, TimeUnit.SECONDS)
                verify(exactly = 4) { api.pageAt(0) }

                presenter.observePolling(owner.lifecycle)
                scheduler.triggerActions()
                verify(exactly = 5) { api.pageAt(0) }
                scheduler.advanceTimeBy(interval, TimeUnit.SECONDS)
                verify(exactly = 6) { api.pageAt(0) }
            } finally {
                presenter.bag.dispose()
                RxJavaPlugins.setComputationSchedulerHandler(previousScheduler)
            }
        }
    }

    describe("workflow polling") {
        it("recovers matching builds beyond the first page after pagination ends") {
            val scheduler = TestScheduler()
            val previousScheduler = RxJavaPlugins.getComputationSchedulerHandler()
            RxJavaPlugins.setComputationSchedulerHandler { scheduler }
            try {
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
                try {
                    presenter.getBuilds()
                    presenter.isLoading.value shouldBe true
                    presenter.getBuilds()
                    scheduler.triggerActions()
                    verify(exactly = 1) { api.pageAt(0) }
                    presenter.getBuilds()
                    scheduler.triggerActions()
                    presenter.hasMore.value shouldBe false

                    val owner = PollingLifecycleOwner()
                    owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)
                    presenter.observePolling(owner.lifecycle)
                    scheduler.triggerActions()
                    scheduler.advanceTimeBy(BuildListPresenter.BUILD_LIST_POLLING_SECONDS, TimeUnit.SECONDS)

                    builds.assertValues(emptyList(), listOf(known), listOf(missed, known))
                    verify(exactly = 2) { api.pageAt(20) }
                    verify(exactly = 0) { api.pageAt(40) }
                    presenter.page.value shouldBe 2
                    presenter.hasMore.value shouldBe false
                } finally {
                    presenter.bag.dispose()
                    builds.dispose()
                }
            } finally {
                RxJavaPlugins.setComputationSchedulerHandler(previousScheduler)
            }
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

private class PollingLifecycleOwner : LifecycleOwner {
    override val lifecycle = LifecycleRegistry.createUnsafe(this)
}
