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
        every { queuedAt } returns null
    }

    fun CircleCIAPIClientV1_1.pageAt(offset: Int) =
        getProjectBuilds(vcsType = "github", userName = "owner", project = "repo", offset = offset, limit = 20)

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
