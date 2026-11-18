package com.unhappychoice.norimaki.presentation.presenter

import com.github.unhappychoice.circleci.v1.response.Build
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.reactivex.Observable
import io.reactivex.schedulers.TestScheduler
import io.reactivex.subjects.PublishSubject
import java.util.concurrent.TimeUnit

class BuildStepOutputTest : DescribeSpec({
    describe("pollActionOutputs()") {
        it("loads both actions while running and appends each final line once") {
            val scheduler = TestScheduler()
            val finished = PublishSubject.create<Build>()
            val logs = mapOf(
                "first" to listOf("one\n", "one more\n", "one done\n"),
                "second" to listOf("action two\n", "two more\n", "two done\n")
            )
            val available = logs.mapValues { (_, lines) -> lines.first() }.toMutableMap()
            val observer = pollActionOutputs(logs.keys.toList(), finished, { url, offset ->
                Observable.just(available.getValue(url).toByteArray().drop(offset.toInt()).toByteArray())
            }, scheduler).map { String(it) }.test()

            observer.assertValues("one\n", "action two\n").assertNotComplete()
            logs.forEach { (url, lines) -> available[url] = lines.take(2).joinToString("") }
            scheduler.advanceTimeBy(1, TimeUnit.SECONDS)
            observer.assertValues("one\n", "action two\n", "one more\n", "two more\n")

            logs.forEach { (url, lines) -> available[url] = lines.joinToString("") }
            finished.onNext(mockk<Build>())
            scheduler.advanceTimeBy(10, TimeUnit.SECONDS)

            observer.assertResult("one\n", "action two\n", "one more\n", "two more\n", "one done\n", "two done\n")
        }

        it("drains from the latest offset when completion arrives during a read") {
            val finished = PublishSubject.create<Build>()
            val requestedOffsets = mutableListOf<Pair<String, Long>>()
            val finalOffsets = mutableListOf<Pair<String, Long>>()
            val observer = pollActionOutputs(listOf("first", "second"), finished, { url, offset ->
                Observable.fromCallable {
                    requestedOffsets += url to offset
                    if (url == "first" && offset == 0L) finished.onNext(mockk<Build>())
                    "$url\n".toByteArray()
                }
            }, TestScheduler(), fetchFinalOutput = { url, offset ->
                finalOffsets += url to offset
                Observable.just("$url done\n".toByteArray())
            }).map { String(it) }.test()

            observer.assertResult("first\n", "second\n", "first done\n", "second done\n")
            requestedOffsets shouldBe listOf("first" to 0L, "second" to 0L)
            finalOffsets shouldBe listOf("first" to 6L, "second" to 7L)
        }
    }
})
