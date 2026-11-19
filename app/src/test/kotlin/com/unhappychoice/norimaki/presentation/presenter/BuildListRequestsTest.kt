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
            val requests = BuildListRequests(null, scheduler, scheduler, scheduler, { Observable.empty() }) { offset ->
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

        it("retries the same offset after a page is disposed during the background merge") {
            val scheduler = TestScheduler()
            val computation = TestScheduler()
            val offsets = mutableListOf<Int>()
            val first = build(42, "running")
            val next = build(41, "success")
            val requests = BuildListRequests(null, scheduler, computation, scheduler, { Observable.empty() }) { offset ->
                offsets.add(offset)
                Observable.just(listOf(if (offset == 0) first else next))
            }

            val builds = requests.builds.test()
            val polling = requests.poll(10)
            scheduler.triggerActions()
            computation.triggerActions()
            scheduler.triggerActions()
            val page = checkNotNull(requests.loadNextPage())
            scheduler.triggerActions()
            requests.isLoading.value shouldBe true

            page.dispose()
            computation.triggerActions()
            scheduler.triggerActions()
            requests.isLoading.value shouldBe false
            requests.page.value shouldBe 1
            builds.assertValues(emptyList(), listOf(first))

            checkNotNull(requests.loadNextPage())
            scheduler.triggerActions()
            computation.triggerActions()
            scheduler.triggerActions()
            offsets shouldBe listOf(0, 20, 20)
            builds.assertValues(emptyList(), listOf(first), listOf(first, next))
            polling.dispose()
        }

        it("keeps a newer poll snapshot while adding unseen builds from a late page") {
            val scheduler = TestScheduler()
            val olderPage = PublishSubject.create<List<Build>>()
            val running = build(42, "running")
            val successful = build(42, "success")
            val unseen = build(41, "success")
            val offsets = mutableListOf<Int>()
            val requests = BuildListRequests(null, scheduler, scheduler, scheduler, { Observable.empty() }) { offset ->
                offsets.add(offset)
                when {
                    offsets.size == 1 -> Observable.just(listOf(running))
                    offset == 0 -> Observable.just(listOf(successful))
                    else -> olderPage
                }
            }

            val builds = requests.builds.test()
            val polling = requests.poll(10)
            scheduler.triggerActions()
            requests.loadNextPage()
            scheduler.triggerActions()
            scheduler.advanceTimeBy(10, SECONDS)
            offsets shouldBe listOf(0, 20, 0)
            builds.values().last().map { it.status } shouldBe listOf("success")

            olderPage.onNext(listOf(running, unseen))
            olderPage.onComplete()
            scheduler.triggerActions()
            builds.values().last().map { it.buildNum to it.status } shouldBe
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
    every { lifecycle } returns if (buildStatus == "running") "running" else "finished"
    every { status } returns buildStatus
    every { queuedAt } returns Date(number.toLong())
}
