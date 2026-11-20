package com.unhappychoice.norimaki.presentation.presenter

import com.github.unhappychoice.circleci.v1.response.Build
import com.github.unhappychoice.circleci.v1.response.BuildStep
import com.unhappychoice.norimaki.domain.model.BuildStepId
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.reactivex.Observable
import io.reactivex.schedulers.TestScheduler
import java.util.concurrent.TimeUnit

class BuildStepLookupTest : DescribeSpec({
    val stepId = BuildStepId(null, legacyName = "Run tests")
    val step = mockk<BuildStep> { every { name } returns "Run tests" }
    val running = mockk<Build> {
        every { steps } returns emptyList()
        every { lifecycle } returns "running"
    }
    val finished = mockk<Build> {
        every { steps } returns emptyList()
        every { lifecycle } returns "finished"
    }

    describe("pollBuildStep()") {
        it("retries a failed refresh while waiting for the requested step") {
            val scheduler = TestScheduler()
            val resolved = mockk<Build> { every { steps } returns listOf(step) }
            var requests = 0
            val observer = pollBuildStep(stepId, scheduler) {
                when (++requests) {
                    1 -> Observable.just(running)
                    2 -> Observable.error(IllegalStateException("Build refresh failed"))
                    else -> Observable.just(resolved)
                }
            }.test()

            observer.assertNoValues().assertNotComplete()
            scheduler.advanceTimeBy(BuildPresenter.BUILD_POLLING_SECONDS * 3, TimeUnit.SECONDS)

            observer.assertResult(resolved to step)
            requests shouldBe 3
        }

        it("fails when the build finishes without the requested step") {
            val scheduler = TestScheduler()
            var requests = 0
            val observer = pollBuildStep(stepId, scheduler) {
                Observable.just(if (++requests == 1) running else finished)
            }.test()

            observer.assertNoValues().assertNotComplete()
            scheduler.advanceTimeBy(BuildPresenter.BUILD_POLLING_SECONDS * 3, TimeUnit.SECONDS)

            observer.assertFailure(NoSuchElementException::class.java)
            requests shouldBe 2
        }
    }
})
