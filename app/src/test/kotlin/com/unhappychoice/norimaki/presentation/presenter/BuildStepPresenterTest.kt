package com.unhappychoice.norimaki.presentation.presenter

import com.github.unhappychoice.circleci.CircleCIAPIClientV1_1
import com.unhappychoice.norimaki.domain.model.BuildId
import com.unhappychoice.norimaki.domain.model.BuildStepId
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
import java.util.concurrent.TimeUnit
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.singleton

class BuildStepPresenterTest : DescribeSpec({
    it("exposes the initial build load failure without retrying") {
        val scheduler = TestScheduler()
        val api = mockk<CircleCIAPIClientV1_1> {
            every { getBuild("github", "owner", "repo", 123) } returns
                Observable.error(IllegalStateException("Build load failed"))
        }
        val presenter = BuildStepPresenter().apply {
            di = DI {
                bind<BuildId>() with singleton { BuildId("github", "owner", "repo", 123) }
                bind<BuildStepId>() with singleton { BuildStepId(null, legacyName = "Run tests") }
                bind<CircleCIAPIClientV1_1>() with singleton { api }
            }
        }
        val failures = presenter.logLoadFailed.asObservable().distinctUntilChanged().test()
        var stepLoaded = false

        try {
            RxJavaPlugins.setComputationSchedulerHandler { scheduler }
            RxJavaPlugins.setIoSchedulerHandler { Schedulers.trampoline() }
            RxAndroidPlugins.setInitMainThreadSchedulerHandler { Schedulers.trampoline() }
            RxAndroidPlugins.setMainThreadSchedulerHandler { Schedulers.trampoline() }

            presenter.loadStep { stepLoaded = true }

            failures.assertValues(false, true)
            stepLoaded shouldBe false
            scheduler.advanceTimeBy(BuildPresenter.BUILD_POLLING_SECONDS * 3, TimeUnit.SECONDS)
            verify(exactly = 1) { api.getBuild("github", "owner", "repo", 123) }
        } finally {
            presenter.bag.clear()
            failures.dispose()
            RxAndroidPlugins.reset()
            RxJavaPlugins.reset()
        }
    }
})
