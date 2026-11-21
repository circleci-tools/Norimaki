package com.unhappychoice.norimaki.presentation.presenter

import com.unhappychoice.norimaki.infrastructure.output.OUTPUT_LIMIT_MESSAGE
import com.unhappychoice.norimaki.infrastructure.output.RawOutputChunk
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.reactivex.Observable
import io.reactivex.schedulers.TestScheduler
import io.reactivex.subjects.PublishSubject
import java.util.concurrent.TimeUnit

class BuildStepOutputTest : DescribeSpec({
    describe("pollActionOutputs()") {
        it("loads both actions while running and appends each final line once") {
            val scheduler = TestScheduler()
            val finished = PublishSubject.create<ActionSnapshot>()
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
            finished.onNext(ActionSnapshot(emptyList(), finished = true))
            scheduler.advanceTimeBy(10, TimeUnit.SECONDS)

            observer.assertResult("one\n", "action two\n", "one more\n", "two more\n", "one done\n", "two done\n")
        }

        it("discovers new actions after all known actions are capped and never reads capped actions again") {
            val scheduler = TestScheduler()
            val finished = PublishSubject.create<ActionSnapshot>()
            val requests = mutableListOf<Pair<String, Long>>()
            val observer = pollActionOutputs(listOf("capped"), finished, { url, offset ->
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

            observer.assertValues("prefix" + OUTPUT_LIMIT_MESSAGE).assertNotComplete()
            finished.onNext(ActionSnapshot(listOf("capped", "other"), finished = false))
            scheduler.advanceTimeBy(2, TimeUnit.SECONDS)
            finished.onNext(ActionSnapshot(emptyList(), finished = true))

            observer.assertResult("prefix" + OUTPUT_LIMIT_MESSAGE, "other", " done")
            requests shouldBe listOf("capped" to 0L, "other" to 0L, "other" to 5L, "other" to 5L)
        }

        it("flushes a capped final read once and still drains the next action") {
            val finished = PublishSubject.create<ActionSnapshot>()
            val bytes = "é".toByteArray()
            val observer = pollActionOutputs(
                listOf("capped", "other"),
                finished,
                { _, _ -> Observable.just(RawOutputChunk(byteArrayOf())) },
                TestScheduler(),
                fetchFinalOutput = { url, _ ->
                    val chunk = if (url == "capped") {
                        RawOutputChunk(bytes.copyOf(1), limitReached = true)
                    } else {
                        RawOutputChunk("last line".toByteArray())
                    }
                    Observable.just(chunk)
                }
            ).test()

            finished.onNext(ActionSnapshot(emptyList(), finished = true))

            observer.assertResult("\uFFFD" + OUTPUT_LIMIT_MESSAGE, "last line")
        }

        it("keeps split UTF-8 sequences separate and completes them during the final drain") {
            val finished = PublishSubject.create<ActionSnapshot>()
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
            finished.onNext(ActionSnapshot(emptyList(), finished = true))

            observer.assertResult("é", "あ")
            finalOffsets shouldBe listOf("first" to 1L, "second" to 2L)
        }

        it("keeps split hyperlink controls local to an action through the final drain") {
            val scheduler = TestScheduler()
            val finished = PublishSubject.create<ActionSnapshot>()
            val chunks = mapOf(
                "first" to listOf("before \u001B]8;id=", "hidden;https://example.test\u001B", "\\Report\u001B]8;;\u0007"),
                "second" to listOf("other output\n", "still running\n", "done\n")
            ).mapValues { (_, parts) -> parts.map { it.toByteArray() } }
            var poll = 0
            val observer = pollActionOutputs(chunks.keys.toList(), finished, { url, _ ->
                Observable.just(RawOutputChunk(chunks.getValue(url)[poll]))
            }, scheduler, fetchFinalOutput = { url, offset ->
                val parts = chunks.getValue(url)
                offset shouldBe parts.take(2).sumOf { it.size.toLong() }
                Observable.just(RawOutputChunk(parts.last()))
            }).test()

            observer.assertValues("before ", "other output\n").assertNotComplete()
            poll++
            scheduler.advanceTimeBy(1, TimeUnit.SECONDS)
            observer.assertValues("before ", "other output\n", "still running\n").assertNotComplete()

            finished.onNext(ActionSnapshot(emptyList(), finished = true))
            observer.assertResult("before ", "other output\n", "still running\n", "Report", "done\n")
        }

        it("drains from the latest offset when completion arrives during a read") {
            val finished = PublishSubject.create<ActionSnapshot>()
            val requestedOffsets = mutableListOf<Pair<String, Long>>()
            val finalOffsets = mutableListOf<Pair<String, Long>>()
            val observer = pollActionOutputs(listOf("first", "second"), finished, { url, offset ->
                Observable.fromCallable {
                    requestedOffsets += url to offset
                    if (url == "first" && offset == 0L) finished.onNext(ActionSnapshot(emptyList(), finished = true))
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

        it("starts the final read as soon as a slow batch settles") {
            val scheduler = TestScheduler()
            val finished = PublishSubject.create<ActionSnapshot>()
            val log = "abcdefghijklmnopqrstuvwxyz0123456789"
            val requestedOffsets = mutableListOf<Long>()
            val finalOffsets = mutableListOf<Long>()
            val observer = pollActionOutputs(listOf("action"), finished, { _, offset ->
                requestedOffsets += offset
                Observable.timer(2_500, TimeUnit.MILLISECONDS, scheduler)
                    .map { RawOutputChunk(log.substring(offset.toInt(), offset.toInt() + 1).toByteArray()) }
            }, scheduler, fetchFinalOutput = { _, offset ->
                finalOffsets += offset
                Observable.just(RawOutputChunk(log.substring(offset.toInt()).toByteArray()))
            }).test()

            scheduler.advanceTimeTo(61, TimeUnit.SECONDS)
            finished.onNext(ActionSnapshot(emptyList(), finished = true))
            observer.assertNotComplete()
            finalOffsets shouldBe emptyList()

            scheduler.advanceTimeTo(62_500, TimeUnit.MILLISECONDS)
            observer.assertComplete().assertNoErrors()
            requestedOffsets shouldBe (0L..20L).toList()
            finalOffsets shouldBe listOf(21L)
            observer.values().joinToString("") shouldBe log
        }
    }
})
