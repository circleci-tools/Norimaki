package com.unhappychoice.norimaki.presentation.presenter

import com.github.unhappychoice.circleci.CircleCIAPIClientV1_1
import com.github.unhappychoice.circleci.v1.response.Build
import com.unhappychoice.norimaki.domain.service.SessionService
import com.unhappychoice.norimaki.presentation.view.BuildView
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.reactivex.Observable
import io.reactivex.android.plugins.RxAndroidPlugins
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import mortar.MortarScope
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.singleton

class BuildPresenterTest : DescribeSpec({
    lateinit var scope: MortarScope
    lateinit var presenter: BuildPresenter
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
        scope = presenter.attachToTestScope(mockk<BuildView>())
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
    }
})
