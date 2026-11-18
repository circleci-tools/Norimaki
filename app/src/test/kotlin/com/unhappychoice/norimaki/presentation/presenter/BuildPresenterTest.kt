package com.unhappychoice.norimaki.presentation.presenter

import com.github.unhappychoice.circleci.CircleCIAPIClientV1_1
import com.github.unhappychoice.circleci.v1.response.Build
import com.github.unhappychoice.circleci.v1.response.BuildStep
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import com.unhappychoice.norimaki.MainActivity
import com.unhappychoice.norimaki.presentation.screen.BuildStepScreen
import flow.Flow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import io.reactivex.Observable
import io.reactivex.android.plugins.RxAndroidPlugins
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.schedulers.Schedulers
import io.reactivex.schedulers.TestScheduler
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.singleton
import java.util.concurrent.TimeUnit

class BuildPresenterTest : DescribeSpec({
    it("should only publish changed snapshots and open the latest step") {
        val scheduler = TestScheduler()
        val first = step("https://example.com/first")
        val duplicate = step("https://example.com/first")
        val changed = step("https://example.com/latest")
        (first == duplicate && first !== duplicate) shouldBe true

        val build = pollResult(first)
        val api = mockk<CircleCIAPIClientV1_1>()
        every { api.getBuild("github", "owner", "repo", 42) } returnsMany
            listOf(build, pollResult(duplicate), pollResult(changed, "finished")).map { Observable.just(it) }

        val activity = mockk<MainActivity>()
        val presenter = BuildPresenter().apply {
            di = DI {
                bind<Build>() with singleton { build }
                bind<MainActivity>() with singleton { activity }
                bind<CircleCIAPIClientV1_1>() with singleton { api }
            }
        }
        val snapshots = presenter.steps.asObservable().test()

        try {
            RxJavaPlugins.setIoSchedulerHandler { Schedulers.trampoline() }
            RxJavaPlugins.setComputationSchedulerHandler { scheduler }
            RxAndroidPlugins.setInitMainThreadSchedulerHandler { Schedulers.trampoline() }
            RxAndroidPlugins.setMainThreadSchedulerHandler { Schedulers.trampoline() }

            presenter.pollBuild()
            snapshots.assertValues(emptyList(), listOf(first))

            scheduler.advanceTimeBy(BuildPresenter.BUILD_POLLING_SECONDS, TimeUnit.SECONDS)
            verify(exactly = 2) { api.getBuild("github", "owner", "repo", 42) }
            snapshots.assertValues(emptyList(), listOf(first))

            scheduler.advanceTimeBy(BuildPresenter.BUILD_POLLING_SECONDS, TimeUnit.SECONDS)
            snapshots.assertValues(emptyList(), listOf(first), listOf(changed))

            val flow = mockk<Flow>(relaxed = true)
            val screen = slot<BuildStepScreen>()
            mockkStatic(Flow::class)
            every { Flow.get(activity) } returns flow
            presenter.goToBuildStepScreen(snapshots.values().last().single())
            verify(exactly = 1) { flow.set(capture(screen)) }
            (screen.captured.buildStep === changed) shouldBe true
        } finally {
            presenter.bag.clear()
            snapshots.dispose()
            unmockkStatic(Flow::class)
            RxAndroidPlugins.reset()
            RxJavaPlugins.reset()
        }
    }
})

private fun step(outputUrl: String): BuildStep = GsonBuilder()
    .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
    .create()
    .fromJson(
        """{"name":"test","actions":[{"output_url":"$outputUrl"}]}""",
        BuildStep::class.java
    )

private fun pollResult(step: BuildStep, state: String = "running"): Build = mockk {
    every { lifecycle } returns state
    every { steps } returns listOf(step)
    every { vcsUrl } returns "https://github.com/owner/repo"
    every { username } returns "owner"
    every { reponame } returns "repo"
    every { buildNum } returns 42
}
