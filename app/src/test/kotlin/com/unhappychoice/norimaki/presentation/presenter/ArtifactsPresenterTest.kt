package com.unhappychoice.norimaki.presentation.presenter

import com.github.unhappychoice.circleci.CircleCIAPIClientV1_1
import com.github.unhappychoice.circleci.v1.response.Artifact
import com.unhappychoice.norimaki.domain.model.BuildId
import com.unhappychoice.norimaki.domain.service.SessionService
import com.unhappychoice.norimaki.presentation.view.ArtifactsView
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifyOrder
import io.reactivex.Observable
import io.reactivex.android.plugins.RxAndroidPlugins
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.schedulers.Schedulers
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.singleton
import java.io.IOException

class ArtifactsPresenterTest : DescribeSpec({
    it("loads artifacts for the current job and retries that job after a request failure") {
        RxJavaPlugins.setIoSchedulerHandler { Schedulers.trampoline() }
        RxAndroidPlugins.setInitMainThreadSchedulerHandler { Schedulers.trampoline() }
        RxAndroidPlugins.setMainThreadSchedulerHandler { Schedulers.trampoline() }

        val build = BuildId("bitbucket", "team", "repo", 42)
        val artifact = Artifact(0, "reports/check.txt", "check.txt", "https://dl.circleci.com/file")
        val api = mockk<CircleCIAPIClientV1_1> {
            every { getArtifacts("bitbucket", "team", "repo", 42) } returnsMany listOf(
                Observable.error<List<Artifact>>(IOException()),
                Observable.just(listOf(artifact))
            )
        }
        val presenter = ArtifactsPresenter().apply {
            di = DI {
                bind<BuildId>() with singleton { build }
                bind<SessionService>() with singleton { testSession(api) }
            }
        }
        val view = mockk<ArtifactsView>(relaxUnitFun = true)
        val scope = presenter.attachToTestScope(view)
        try {
            presenter.render()
            presenter.load()

            verifyOrder {
                api.getArtifacts("bitbucket", "team", "repo", 42)
                view.render(ArtifactListState.Failed)
                api.getArtifacts("bitbucket", "team", "repo", 42)
                view.render(ArtifactListState.Loaded(listOf(artifact)))
            }
        } finally {
            scope.destroy()
            RxAndroidPlugins.reset()
            RxJavaPlugins.reset()
        }
    }
})
