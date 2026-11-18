package com.unhappychoice.norimaki.domain.model

import com.github.unhappychoice.circleci.v1.response.Build
import com.unhappychoice.norimaki.extension.polling
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.reactivex.Observable
import io.reactivex.schedulers.TestScheduler
import java.util.concurrent.TimeUnit

class BuildListRefreshTest : DescribeSpec({
    describe("Build list polling") {
        it("refreshes a running build loaded by pagination until it succeeds") {
            verifyPolling(initiallyOnFirstPage = false)
        }

        it("starts fetching details once a running build leaves the first page") {
            verifyPolling(initiallyOnFirstPage = true)
        }

        it("refreshes other missing builds when a detail request fails") {
            val unavailable = build(1, "running")
            val running = build(2, "running")
            val finished = build(2, "finished", "success")
            val latest = listOf(build(3, "running"))

            refreshBuildList(listOf(unavailable, running), latest, null) {
                if (it == unavailable) Observable.error(IllegalStateException()) else Observable.just(finished)
            }.test().assertResult(latest, listOf(finished))
        }
    }
})

private fun verifyPolling(initiallyOnFirstPage: Boolean) {
    val scheduler = TestScheduler()
    val running = build(1, "running")
    val finished = build(1, "finished", "success")
    val newerBuilds = (2..21).map { build(it, "finished", "success") }
    var displayed = if (initiallyOnFirstPage) emptyList() else listOf(running)
    var latest = if (initiallyOnFirstPage) listOf(running) else newerBuilds
    var detailResponse = running
    val detailRequests = mutableListOf<Build>()

    val subscription = polling(10, scheduler = scheduler) {
        refreshBuildList(displayed, latest, null) {
            detailRequests.add(it)
            Observable.just(detailResponse)
        }
    }.subscribe { displayed = displayed.addDistinctByNumber(it) }

    detailRequests.size shouldBe if (initiallyOnFirstPage) 0 else 1
    latest = newerBuilds
    scheduler.advanceTimeBy(10, TimeUnit.SECONDS)
    displayed.single { it.buildNum == 1 }.status shouldBe "running"
    detailResponse = finished
    scheduler.advanceTimeBy(10, TimeUnit.SECONDS)
    displayed.single { it.buildNum == 1 }.status shouldBe "success"
    detailRequests shouldBe List(if (initiallyOnFirstPage) 2 else 3) { running }

    detailRequests.clear()
    scheduler.advanceTimeBy(20, TimeUnit.SECONDS)
    detailRequests shouldBe emptyList()
    subscription.dispose()
}

private fun build(number: Int, state: String, result: String = state): Build = mockk {
    every { username } returns "circleci-tools"
    every { reponame } returns "Norimaki"
    every { buildNum } returns number
    every { lifecycle } returns state
    every { status } returns result
}
