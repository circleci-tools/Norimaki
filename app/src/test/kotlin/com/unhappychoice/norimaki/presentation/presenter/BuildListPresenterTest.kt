package com.unhappychoice.norimaki.presentation.presenter

import com.github.unhappychoice.circleci.CircleCIAPIClientV1_1
import com.github.unhappychoice.circleci.v1.response.Build
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
import java.util.Date
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

    fun presenter(api: CircleCIAPIClientV1_1) = BuildListPresenter().apply {
        di = DI {
            bind<CircleCIAPIClientV1_1>() with singleton { api }
            bind<String>() with singleton { "github/owner/repo" }
        }
    }

    fun build(number: Int): Build = mockk {
        every { username } returns "owner"
        every { reponame } returns "repo"
        every { vcsUrl } returns "https://github.com/owner/repo"
        every { buildNum } returns number
        every { queuedAt } returns Date(number.toLong())
    }

    fun CircleCIAPIClientV1_1.pageAt(offset: Int) =
        getProjectBuilds(vcsType = "github", userName = "owner", project = "repo", offset = offset, limit = 20)

    describe("project build polling") {
        it("recovers builds beyond the first page after pagination ends") {
            val scheduler = TestScheduler()
            val previousScheduler = RxJavaPlugins.getComputationSchedulerHandler()
            RxJavaPlugins.setComputationSchedulerHandler { scheduler }
            try {
                val api = mockk<CircleCIAPIClientV1_1>()
                val known = build(1)
                val missed = build(2)
                val firstPage = (22 downTo 3).map(::build)
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

                    presenter.pollLatestBuilds()
                    scheduler.triggerActions()
                    scheduler.advanceTimeBy(BuildListPresenter.BUILD_LIST_POLLING_SECONDS, TimeUnit.SECONDS)

                    builds.assertValues(emptyList(), listOf(known), firstPage + listOf(missed, known))
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

            repeat(4) { presenter.getBuilds() }

            builds.values().last().toSet() shouldBe setOf(latest, older)
            presenter.hasMore.value shouldBe false
            verify(exactly = 0) { api.pageAt(60) }
        }
    }
})
