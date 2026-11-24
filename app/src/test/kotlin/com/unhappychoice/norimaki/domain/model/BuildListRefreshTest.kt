package com.unhappychoice.norimaki.domain.model

import com.github.unhappychoice.circleci.v1.response.Build
import com.unhappychoice.norimaki.extension.polling
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.reactivex.Observable
import io.reactivex.schedulers.TestScheduler
import io.reactivex.subjects.PublishSubject
import java.util.concurrent.TimeUnit

class BuildListRefreshTest : DescribeSpec({
    describe("Build list polling") {
        it("refreshes a running build loaded by pagination until it succeeds") {
            verifyPolling(initiallyOnFirstPage = false)
        }

        it("starts fetching details once a running build leaves the first page") {
            verifyPolling(initiallyOnFirstPage = true)
        }

        it("publishes the latest page before details finish and batches successes around a failure") {
            val running = (1..3).map { build(it, "running") }
            val firstFinished = build(1, "finished", "success")
            val lastFinished = build(3, "finished", "success")
            val latest = listOf(build(4, "running"))
            val pending = PublishSubject.create<Build>()

            val updates = refreshBuildList(running, latest) {
                when (it.buildNum) {
                    1 -> Observable.just(firstFinished)
                    2 -> Observable.error(IllegalStateException())
                    else -> pending
                }
            }.test()

            updates.assertValues(latest).assertNotComplete()
            pending.onNext(lastFinished)
            pending.onComplete()
            updates.assertResult(latest, listOf(firstFinished, lastFinished))
        }

        it("runs detail requests concurrently up to the configured limit and batches successes around a failure") {
            val running = (1..5).map { build(it, "running") }
            val pending = (1..5).associateWith { PublishSubject.create<Build>() }
            val requested = mutableListOf<Int>()

            val updates = refreshBuildList(running, emptyList()) { build ->
                requested.add(build.buildNum!!)
                pending.getValue(build.buildNum!!)
            }.test()

            requested shouldBe (1..BUILD_DETAIL_FETCH_CONCURRENCY).toList()

            val finished = (1..5).associateWith { build(it, "finished", "success") }
            pending.getValue(1).onNext(finished.getValue(1))
            pending.getValue(1).onComplete()

            requested shouldBe (1..5).toList()

            pending.getValue(2).onError(IllegalStateException())
            pending.getValue(3).onNext(finished.getValue(3))
            pending.getValue(3).onComplete()
            pending.getValue(4).onNext(finished.getValue(4))
            pending.getValue(4).onComplete()
            pending.getValue(5).onNext(finished.getValue(5))
            pending.getValue(5).onComplete()

            updates.assertResult(
                emptyList(),
                listOf(finished.getValue(1), finished.getValue(3), finished.getValue(4), finished.getValue(5))
            )
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
        refreshBuildList(displayed, latest) {
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
    every { vcsUrl } returns "https://github.com/circleci-tools/Norimaki"
    every { reponame } returns "Norimaki"
    every { buildNum } returns number
    every { lifecycle } returns state
    every { status } returns result
}
