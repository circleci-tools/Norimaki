package com.unhappychoice.norimaki.presentation.presenter

import com.github.unhappychoice.circleci.v1.response.Build
import com.unhappychoice.norimaki.infrastructure.output.OUTPUT_LIMIT_MESSAGE
import com.unhappychoice.norimaki.infrastructure.output.RawOutputChunk
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
                Observable.just(RawOutputChunk(available.getValue(url).toByteArray().drop(offset.toInt()).toByteArray()))
            }, scheduler).test()

            observer.assertValues("one\n", "action two\n").assertNotComplete()
            logs.forEach { (url, lines) -> available[url] = lines.take(2).joinToString("") }
            scheduler.advanceTimeBy(1, TimeUnit.SECONDS)
            observer.assertValues("one\n", "action two\n", "one more\n", "two more\n")

            logs.forEach { (url, lines) -> available[url] = lines.joinToString("") }
            finished.onNext(mockk<Build>())
            scheduler.advanceTimeBy(10, TimeUnit.SECONDS)

            observer.assertResult("one\n", "action two\n", "one more\n", "two more\n", "one done\n", "two done\n")
        }

        it("shows the limit once and skips both polling and final reads for capped actions") {
            val scheduler = TestScheduler()
            val finished = PublishSubject.create<Build>()
            val requests = mutableListOf<Pair<String, Long>>()
            val observer = pollActionOutputs(listOf("capped", "other"), finished, { url, offset ->
                requests += url to offset
                val chunk = when {
                    url == "capped" -> RawOutputChunk("prefix".toByteArray(), limitReached = true)
                    offset == 0L -> RawOutputChunk("other".toByteArray())
                    else -> RawOutputChunk(byteArrayOf())
                }
                Observable.just(chunk)
            }, scheduler, fetchFinalOutput = { url, offset ->
                requests += url to offset
                Observable.just(RawOutputChunk(" done".toByteArray()))
            }).test()

            scheduler.advanceTimeBy(1, TimeUnit.SECONDS)
            finished.onNext(mockk<Build>())

            observer.assertResult("prefix" + OUTPUT_LIMIT_MESSAGE, "other", " done")
            requests shouldBe listOf("capped" to 0L, "other" to 0L, "other" to 5L, "other" to 5L)
        }

        it("keeps split UTF-8 sequences separate and completes them during the final drain") {
            val finished = PublishSubject.create<Build>()
            val first = "é".toByteArray()
            val second = "あ".toByteArray()
            val prefixes = mapOf("first" to first.take(1), "second" to second.take(2))
            val logs = mapOf("first" to first, "second" to second)
            val finalOffsets = mutableListOf<Pair<String, Long>>()

            val observer = pollActionOutputs(logs.keys.toList(), finished, { url, _ ->
                Observable.just(RawOutputChunk(prefixes.getValue(url).toByteArray()))
            }, TestScheduler(), fetchFinalOutput = { url, offset ->
                finalOffsets += url to offset
                Observable.just(RawOutputChunk(logs.getValue(url).drop(offset.toInt()).toByteArray()))
            }).test()

            observer.assertNoValues().assertNotComplete()
            finished.onNext(mockk<Build>())

            observer.assertResult("é", "あ")
            finalOffsets shouldBe listOf("first" to 1L, "second" to 2L)
        }

        it("drains from the latest offset when completion arrives during a read") {
            val finished = PublishSubject.create<Build>()
            val requestedOffsets = mutableListOf<Pair<String, Long>>()
            val finalOffsets = mutableListOf<Pair<String, Long>>()
            val observer = pollActionOutputs(listOf("first", "second"), finished, { url, offset ->
                Observable.fromCallable {
                    requestedOffsets += url to offset
                    if (url == "first" && offset == 0L) finished.onNext(mockk<Build>())
                    RawOutputChunk("$url\n".toByteArray())
                }
            }, TestScheduler(), fetchFinalOutput = { url, offset ->
                finalOffsets += url to offset
                Observable.just(RawOutputChunk("$url done\n".toByteArray()))
            }).test()

            observer.assertResult("first\n", "second\n", "first done\n", "second done\n")
            requestedOffsets shouldBe listOf("first" to 0L, "second" to 0L)
            finalOffsets shouldBe listOf("first" to 6L, "second" to 7L)
        }
    }
})
