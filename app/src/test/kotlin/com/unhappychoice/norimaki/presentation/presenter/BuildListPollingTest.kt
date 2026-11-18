package com.unhappychoice.norimaki.presentation.presenter

import com.github.unhappychoice.circleci.v1.response.Build
import com.unhappychoice.norimaki.domain.model.addDistinctByNumber
import com.unhappychoice.norimaki.domain.model.sortByQueuedAt
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
            var builds = with(history) {
                fetchPage(0).paginate().blockingFirst().also { fetchPage(20).paginate().blockingFirst() }
            }
            history.hasMore.value shouldBe false

            val scheduler = TestScheduler()
            val polls = polling(10, scheduler = scheduler) { fetchLatestBuilds(builds, fetchPage) }
                .subscribe { builds = builds.addDistinctByNumber(it).sortByQueuedAt() }
            offsets shouldBe listOf(0, 20, 0)
            apiBuilds = allBuilds

            scheduler.advanceTimeBy(10, TimeUnit.SECONDS)
            offsets shouldBe listOf(0, 20, 0, 0, 20)
            builds.map { it.buildNum } shouldBe (41 downTo 1).toList()
            history.hasMore.value shouldBe false
            polls.dispose()
        }

        it("should stop at an empty page when no build overlaps history") {
            val offsets = mutableListOf<Int>()
            val latest = (40 downTo 1).map(::build)
            val pages = fetchLatestBuilds(listOf(build(100))) { offset ->
                offsets.add(offset)
                Observable.just(latest.drop(offset).take(20))
            }

            pages.test().assertResult(latest)
            offsets shouldBe listOf(0, 20, 40)
        }
    }
})
