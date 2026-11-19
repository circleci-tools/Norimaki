package com.unhappychoice.norimaki.presentation.presenter

import com.github.unhappychoice.circleci.v1.response.Build
import com.unhappychoice.norimaki.domain.model.BuildHistory
import com.unhappychoice.norimaki.extension.Variable
import com.unhappychoice.norimaki.extension.polling
import com.unhappychoice.norimaki.presentation.presenter.core.Paginatable
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
        it("should stop at the first unchanged page without reading retained build IDs") {
            val retained = (1000 downTo 1).map(::build)
            val history = checkNotNull(BuildHistory().merge(retained))
            val olderBuild = retained.last()
            clearMocks(olderBuild, answers = false)
            val firstPage = (1000 downTo 981).map(::build)
            val offsets = mutableListOf<Int>()

            fetchLatestBuilds(history) { offset ->
                offsets.add(offset)
                Observable.just(firstPage.takeIf { offset == 0 }.orEmpty())
            }.test().assertResult(firstPage)

            offsets shouldBe listOf(0)
            verify(exactly = 0) { olderBuild.buildNum }
        }

        it("should recover 21 new builds in order after history is exhausted") {
            val pagination = object : Paginatable {
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
            val builds = with(pagination) {
                fetchPage(0).paginate().blockingFirst().also { fetchPage(20).paginate().blockingFirst() }
            }
            var history = checkNotNull(BuildHistory().merge(builds))
            pagination.hasMore.value shouldBe false

            val scheduler = TestScheduler()
            val polls = polling(10, scheduler = scheduler) { fetchLatestBuilds(history, fetchPage) }
                .subscribe { history = history.merge(it) ?: history }
            offsets shouldBe listOf(0, 20, 0)
            apiBuilds = allBuilds

            scheduler.advanceTimeBy(10, TimeUnit.SECONDS)
            offsets shouldBe listOf(0, 20, 0, 0, 20)
            history.builds.map { it.buildNum } shouldBe (41 downTo 1).toList()
            pagination.hasMore.value shouldBe false
            polls.dispose()
        }

        it("should stop at an empty page when no build overlaps history") {
            val offsets = mutableListOf<Int>()
            val latest = (40 downTo 1).map(::build)
            val history = checkNotNull(BuildHistory().merge(listOf(build(100))))
            val pages = fetchLatestBuilds(history) { offset ->
                offsets.add(offset)
                Observable.just(latest.drop(offset).take(20))
            }

            pages.test().assertResult(latest)
            offsets shouldBe listOf(0, 20, 40)
        }
    }
})
