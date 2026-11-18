package com.unhappychoice.norimaki.presentation.presenter

import com.github.unhappychoice.circleci.v1.response.Build
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.reactivex.Observable
import io.reactivex.schedulers.TestScheduler
import io.reactivex.subjects.PublishSubject
import java.util.Date
import java.util.concurrent.TimeUnit.SECONDS

class BuildListRequestsTest : DescribeSpec({
    describe("Build list requests") {
        it("uses the first poll as page zero and advances pagination from it") {
            val scheduler = TestScheduler()
            val initialPage = PublishSubject.create<List<Build>>()
            val offsets = mutableListOf<Int>()
            val requests = BuildListRequests(null, scheduler, scheduler) { offset ->
                offsets.add(offset)
                if (offset == 0) initialPage else Observable.just(emptyList<Build>())
            }

            val polling = requests.poll(10)
            scheduler.triggerActions()
            requests.loadNextPage() shouldBe null
            scheduler.advanceTimeBy(10, SECONDS)
            offsets shouldBe listOf(0)

            initialPage.onNext(listOf(build(42, "running")))
            initialPage.onComplete()
            scheduler.triggerActions()
            requests.loadNextPage()
            scheduler.triggerActions()
            offsets shouldBe listOf(0, 20)
            polling.dispose()
        }

        it("allows pagination to retry the same offset after disposal") {
            val scheduler = TestScheduler()
            val offsets = mutableListOf<Int>()
            val requests = BuildListRequests(null, scheduler, scheduler) { offset ->
                offsets.add(offset)
                if (offset == 0) Observable.just(listOf(build(42, "running"))) else Observable.never()
            }

            val polling = requests.poll(10)
            scheduler.triggerActions()
            val page = checkNotNull(requests.loadNextPage())
            scheduler.triggerActions()
            requests.isLoading.value shouldBe true

            page.dispose()
            requests.isLoading.value shouldBe false
            val retry = checkNotNull(requests.loadNextPage())
            scheduler.triggerActions()
            offsets shouldBe listOf(0, 20, 20)

            retry.dispose()
            polling.dispose()
        }

        it("keeps a newer poll snapshot while adding unseen builds from a late page") {
            val scheduler = TestScheduler()
            val olderPage = PublishSubject.create<List<Build>>()
            val running = build(42, "running")
            val successful = build(42, "success")
            val unseen = build(41, "success")
            val offsets = mutableListOf<Int>()
            val requests = BuildListRequests(null, scheduler, scheduler) { offset ->
                offsets.add(offset)
                when {
                    offsets.size == 1 -> Observable.just(listOf(running))
                    offset == 0 -> Observable.just(listOf(successful))
                    else -> olderPage
                }
            }

            val polling = requests.poll(10)
            scheduler.triggerActions()
            requests.loadNextPage()
            scheduler.triggerActions()
            scheduler.advanceTimeBy(10, SECONDS)
            offsets shouldBe listOf(0, 20, 0)
            requests.builds.value.map { it.status } shouldBe listOf("success")

            olderPage.onNext(listOf(running, unseen))
            olderPage.onComplete()
            scheduler.triggerActions()
            requests.builds.value.map { it.buildNum to it.status } shouldBe
                listOf(42 to "success", 41 to "success")
            polling.dispose()
        }
    }
})

private fun build(number: Int, buildStatus: String): Build = mockk {
    every { vcsUrl } returns null
    every { username } returns "circleci-tools"
    every { reponame } returns "Norimaki"
    every { buildNum } returns number
    every { status } returns buildStatus
    every { queuedAt } returns Date(number.toLong())
}
