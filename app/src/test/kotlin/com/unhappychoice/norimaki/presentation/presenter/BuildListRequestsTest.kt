package com.unhappychoice.norimaki.presentation.presenter

import com.github.unhappychoice.circleci.v1.response.Build
import com.google.gson.Gson
import com.unhappychoice.norimaki.domain.model.uniqueId
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
        it("bounds polling history and pages through evicted builds while new builds arrive") {
            val scheduler = TestScheduler()
            val template = Gson().fromJson(
                """{"username":"circleci-tools","reponame":"Norimaki"}""",
                Build::class.java
            )
            fun historyBuild(number: Int, state: String = "success") = template.copy(
                buildNum = number,
                lifecycle = if (state == "running") "running" else "finished",
                status = state,
                queuedAt = Date(number.toLong())
            )

            val lastInitialBuild = 160
            val runningNumber = lastInitialBuild + 60
            val running = historyBuild(runningNumber, "running")
            val finished = historyBuild(runningNumber)
            val apiBuilds = (lastInitialBuild downTo 1).map { historyBuild(it) }.toMutableList()
            var detailsReady = false
            val detailRequests = mutableListOf<Build>()
            val offsets = mutableListOf<Int>()
            val requests = BuildListRequests(scheduler, scheduler, scheduler, { build ->
                detailRequests.add(build)
                if (detailsReady) Observable.just(finished) else Observable.empty()
            }) { offset ->
                offsets.add(offset)
                Observable.just(apiBuilds.drop(offset).take(BUILD_LIST_PAGE_SIZE))
            }

            val builds = requests.builds.test()
            val initialLoad = requests.start()
            val polling = requests.poll(10, resuming = false, clock = scheduler)
            try {
                scheduler.triggerActions()
                val lastPolledBuild = lastInitialBuild + BUILD_HISTORY_LIMIT + 10
                (lastInitialBuild + 1..lastPolledBuild).forEach { number ->
                    apiBuilds.add(0, if (number == runningNumber) running else historyBuild(number))
                    scheduler.advanceTimeBy(10, SECONDS)
                    val expectedCount = minOf(BUILD_HISTORY_LIMIT, BUILD_LIST_PAGE_SIZE + number - lastInitialBuild)
                    builds.values().last().map { it.buildNum } shouldBe
                        (number downTo number - expectedCount + 1).toList()
                }
                offsets.toSet() shouldBe setOf(0)
                detailRequests.isNotEmpty() shouldBe true
                detailRequests.all { it === running } shouldBe true
                detailsReady = true
                scheduler.advanceTimeBy(10, SECONDS)
                builds.values().last().single { it.buildNum == runningNumber }.status shouldBe "success"

                var oldest = checkNotNull(builds.values().last().last().buildNum)
                var nextNumber = lastPolledBuild
                // Check each successive window against the API order, through the end of history.
                while (requests.hasMore.value) {
                    checkNotNull(requests.loadNextPage())
                    scheduler.triggerActions()
                    val window = builds.values().last().map { it.buildNum }
                    val newOldest = checkNotNull(window.last())
                    if (requests.hasMore.value) {
                        (oldest - newOldest in 1..BUILD_LIST_PAGE_SIZE) shouldBe true
                    }
                    window shouldBe (newOldest + BUILD_HISTORY_LIMIT - 1 downTo newOldest).toList()
                    oldest = newOldest

                    apiBuilds.add(0, historyBuild(++nextNumber))
                    scheduler.advanceTimeBy(10, SECONDS)
                    builds.values().last().map { it.buildNum } shouldBe window
                    builds.values().last().find { it.buildNum == runningNumber }?.status?.let {
                        it shouldBe "success"
                    }
                }
                oldest shouldBe 1
                requests.loadNextPage() shouldBe null

                polling.dispose()
                initialLoad.dispose()
                requests.reset()
                val restarted = requests.start()
                scheduler.triggerActions()
                restarted.dispose()
                builds.values().last().first().buildNum shouldBe nextNumber
            } finally {
                polling.dispose()
                initialLoad.dispose()
                builds.dispose()
            }
        }

        it("recovers unseen builds at a deleted pagination anchor's queue time") {
            val tail = build(21, "success")
            val retained = (40 downTo 22).map { build(it, "success") } + tail
            val sameTime = (20 downTo 19).map { build(it, "success", queueTime = 21) }
            val older = build(18, "success")
            val apiBuilds = retained.dropLast(1) + sameTime + older
            val offsets = mutableListOf<Int>()
            val fetch: (Int) -> Observable<List<Build>> = { offset ->
                offsets.add(offset)
                Observable.just(apiBuilds.drop(offset).take(BUILD_LIST_PAGE_SIZE))
            }
            val retainedIds = retained.map { it.uniqueId() }.toSet()

            fetchOlderBuilds(fetch, tail, retainedIds, BUILD_LIST_PAGE_SIZE).test()
                .assertResult(OlderBuildPage(0, sameTime.take(1)))
            fetchOlderBuilds(fetch, sameTime.first(), retainedIds + sameTime.first().uniqueId(), 0).test()
                .assertResult(OlderBuildPage(BUILD_LIST_PAGE_SIZE, sameTime.drop(1) + older))
            offsets shouldBe listOf(BUILD_LIST_PAGE_SIZE, 0, 0, BUILD_LIST_PAGE_SIZE)
        }

        it("loads page zero once while polling waits for it to settle") {
            val scheduler = TestScheduler()
            val initialPage = PublishSubject.create<List<Build>>()
            val offsets = mutableListOf<Int>()
            val requests = BuildListRequests(scheduler, scheduler, scheduler, { Observable.empty() }) { offset ->
                offsets.add(offset)
                if (offset == 0) initialPage else Observable.just(emptyList<Build>())
            }

            val initialLoad = requests.start()
            val polling = requests.poll(10, resuming = false, clock = scheduler)
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
            initialLoad.dispose()
        }

        it("retries the same offset after a page is disposed during the background merge") {
            val scheduler = TestScheduler()
            val computation = TestScheduler()
            val offsets = mutableListOf<Int>()
            val first = build(42, "running")
            val next = build(41, "success")
            val requests = BuildListRequests(scheduler, computation, scheduler, { Observable.empty() }) { offset ->
                offsets.add(offset)
                Observable.just(listOf(if (offset == 0) first else next))
            }

            val builds = requests.builds.test()
            val initialLoad = requests.start()
            val polling = requests.poll(10, resuming = false, clock = scheduler)
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
            initialLoad.dispose()
        }

        it("recovers a polling gap after publishing a partial refresh") {
            val scheduler = TestScheduler()
            val original = (20 downTo 1).map { build(it, "success") }
            val latest = (41 downTo 21).map { build(it, "success") } + original
            val offsets = mutableListOf<Int>()
            var backfillFails = true
            val requests = BuildListRequests(scheduler, scheduler, scheduler, { Observable.empty() }) { offset ->
                offsets.add(offset)
                when {
                    offsets.size == 1 -> Observable.just(original)
                    offset == 20 && backfillFails -> Observable.error(IllegalStateException("Backfill failed"))
                    else -> Observable.just(latest.drop(offset).take(20))
                }
            }

            val builds = requests.builds.test()
            val initialLoad = requests.start()
            val polling = requests.poll(10, resuming = false, clock = scheduler)
            try {
                scheduler.advanceTimeBy(10, SECONDS)
                builds.values().last() shouldBe latest.take(20) + original

                backfillFails = false
                scheduler.advanceTimeBy(10, SECONDS)
                builds.values().last() shouldBe latest
                offsets shouldBe listOf(0, 0, 20, 0, 20)
            } finally {
                polling.dispose()
                initialLoad.dispose()
                builds.dispose()
            }
        }

        it("keeps a newer poll snapshot while adding unseen builds from a late page") {
            val scheduler = TestScheduler()
            val olderPage = PublishSubject.create<List<Build>>()
            val running = build(42, "running")
            val successful = build(42, "success")
            val unseen = build(41, "success")
            val offsets = mutableListOf<Int>()
            val requests = BuildListRequests(scheduler, scheduler, scheduler, { Observable.empty() }) { offset ->
                offsets.add(offset)
                when {
                    offsets.size == 1 -> Observable.just(listOf(running))
                    offset == 0 -> Observable.just(listOf(successful))
                    else -> olderPage
                }
            }

            val builds = requests.builds.test()
            val initialLoad = requests.start()
            val polling = requests.poll(10, resuming = false, clock = scheduler)
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
            initialLoad.dispose()
        }
    }
})

private fun build(number: Int, buildStatus: String, queueTime: Long = number.toLong()): Build = mockk {
    every { vcsUrl } returns null
    every { username } returns "circleci-tools"
    every { reponame } returns "Norimaki"
    every { buildNum } returns number
    every { lifecycle } returns if (buildStatus == "running") "running" else "finished"
    every { status } returns buildStatus
    every { queuedAt } returns Date(queueTime)
}
