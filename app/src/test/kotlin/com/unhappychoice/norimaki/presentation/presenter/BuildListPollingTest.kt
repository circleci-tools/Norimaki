package com.unhappychoice.norimaki.presentation.presenter

import com.github.unhappychoice.circleci.v1.response.Build
import com.unhappychoice.norimaki.domain.model.BuildHistory
import com.unhappychoice.norimaki.domain.model.uniqueId
import com.unhappychoice.norimaki.extension.Variable
import com.unhappychoice.norimaki.extension.polling
import com.unhappychoice.norimaki.presentation.presenter.core.Paginatable
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.reactivex.Observable
import io.reactivex.schedulers.TestScheduler
import java.util.Date
import java.util.concurrent.TimeUnit

class BuildListPollingTest : DescribeSpec({
    fun build(number: Int): Build = mockk {
        every { username } returns "circleci-tools"
        every { reponame } returns "Norimaki"
        every { vcsUrl } returns null
        every { buildNum } returns number
        every { queuedAt } returns Date(number.toLong())
    }

    describe("fetchLatestBuilds") {
        it("should recover 21 new builds in order after history is exhausted") {
            val history = object : Paginatable {
                override val page = Variable(0)
                override val hasMore = Variable(true)
            }
            val allBuilds = (41 downTo 1).map(::build)
            var apiBuilds = allBuilds.takeLast(20)
            val offsets = mutableListOf<Int>()
            val fetchPage: (Int) -> Observable<List<Build>> = { offset ->
                offsets.add(offset)
                Observable.just(apiBuilds.drop(offset).take(20))
            }
            val builds = with(history) {
                fetchPage(0).paginate().blockingFirst().also { fetchPage(20).paginate().blockingFirst() }
            }
            history.hasMore.value shouldBe false

            val scheduler = TestScheduler()
            val latest = BuildListPolling(fetchPage)
            var buildHistory = BuildHistory().merge(builds)!!
            val polls = polling(10, scheduler = scheduler) {
                latest.fetchLatestBuilds(buildHistory.builds.mapTo(hashSetOf()) { it.uniqueId() })
            }.subscribe { buildHistory = buildHistory.merge(it) ?: buildHistory }
            offsets shouldBe listOf(0, 20, 0)
            apiBuilds = allBuilds

            scheduler.advanceTimeBy(10, TimeUnit.SECONDS)
            offsets shouldBe listOf(0, 20, 0, 0, 20)
            buildHistory.builds.map { it.buildNum } shouldBe (41 downTo 1).toList()
            history.hasMore.value shouldBe false
            polls.dispose()
        }

        it("publishes fetched builds and retries the gap after repeated backfill failures") {
            val allBuilds = (41 downTo 1).map(::build)
            var history = BuildHistory().merge(allBuilds.takeLast(20))!!
            val offsets = mutableListOf<Int>()
            var backfillFails = true
            val latest = BuildListPolling { offset ->
                offsets.add(offset)
                if (offset == 20 && backfillFails) {
                    Observable.error(IllegalStateException("Backfill request failed"))
                } else {
                    Observable.just(allBuilds.drop(offset).take(20))
                }
            }

            val scheduler = TestScheduler()
            val polls = polling(10, scheduler = scheduler) {
                latest.fetchLatestBuilds(history.builds.mapTo(hashSetOf()) { it.uniqueId() })
            }.subscribe { history = history.merge(it) ?: history }
            try {
                val partial = (41 downTo 22).toList() + (20 downTo 1).toList()
                history.builds.map { it.buildNum } shouldBe partial
                scheduler.advanceTimeBy(10, TimeUnit.SECONDS)
                history.builds.map { it.buildNum } shouldBe partial

                backfillFails = false
                scheduler.advanceTimeBy(10, TimeUnit.SECONDS)
                history.builds.map { it.buildNum } shouldBe (41 downTo 1).toList()

                scheduler.advanceTimeBy(10, TimeUnit.SECONDS)
                offsets shouldBe listOf(0, 20, 0, 20, 0, 20, 0)
            } finally {
                polls.dispose()
            }
        }

        it("emits nothing when the first request fails and retries on the next poll") {
            val known = build(1)
            val newest = build(2)
            val requests = listOf(
                Observable.error<List<Build>>(IllegalStateException("First page request failed")),
                Observable.just(listOf(newest, known))
            ).iterator()
            val latest = BuildListPolling { offset ->
                offset shouldBe 0
                requests.next()
            }

            val scheduler = TestScheduler()
            val polls = polling(10, scheduler = scheduler) { latest.fetchLatestBuilds(setOf(known.uniqueId())) }.test()
            try {
                polls.assertNoValues().assertNoErrors()
                scheduler.advanceTimeBy(10, TimeUnit.SECONDS)
                polls.assertValue(listOf(newest, known)).assertNoErrors()
            } finally {
                polls.dispose()
            }
        }

        it("should stop at an empty page when no build overlaps history") {
            val offsets = mutableListOf<Int>()
            val latest = (40 downTo 1).map(::build)
            val polling = BuildListPolling { offset ->
                offsets.add(offset)
                Observable.just(latest.drop(offset).take(20))
            }

            polling.fetchLatestBuilds(setOf(build(100).uniqueId())).test().assertResult(latest)
            offsets shouldBe listOf(0, 20, 40)
        }
    }
})
