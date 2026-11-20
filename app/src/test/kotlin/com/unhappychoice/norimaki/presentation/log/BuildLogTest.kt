package com.unhappychoice.norimaki.presentation.log

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers
import io.reactivex.schedulers.TestScheduler
import io.reactivex.subjects.PublishSubject

class BuildLogTest : DescribeSpec({
    describe("BuildLog") {
        it("formats each incoming chunk once and keeps earlier lines accessible") {
            val source = PublishSubject.create<String>()
            val log = BuildLog()
            val updates = log.changes.test()
            val formatter = LogChunkFormatter()
            val formattedChunks = mutableListOf<String>()
            val subscription = log.render(source, Schedulers.trampoline()) {
                formattedChunks.add(it)
                formatter.format(it)
            }
            val chunks = List(1_000) { index -> "\u001B[32mline ${index.toString().padStart(4, '0')}\u001B[0m\n" }

            source.onNext(chunks.first())
            val firstLine = log[0]
            chunks.drop(1).forEach(source::onNext)

            formattedChunks shouldBe chunks
            log.lineCount shouldBe chunks.size + 1
            (log[0] === firstLine) shouldBe true
            (0 until chunks.size).map { log[it].single().text } shouldBe
                chunks.indices.map { "line ${it.toString().padStart(4, '0')}" }
            updates.values().first() shouldBe LogUpdate(null, 0, 2)
            updates.values().drop(1) shouldBe (1 until chunks.size).map { LogUpdate(it, it + 1, 1) }
            subscription.dispose()
        }

        it("renders only new spans as chunks repeatedly extend the same line") {
            val source = PublishSubject.create<String>()
            val log = BuildLog()
            val cursor = LogLineCursor()
            val updates = log.changes.test()
            val subscription = log.render(source, Schedulers.trampoline())
            val red = 0xFFF00000.toInt()

            source.onNext("prefix ")
            cursor.takeNewSpans(log[0]) shouldBe listOf(LogSpan("prefix ", null))
            val additions = List(1_000) { LogSpan(it.toString(), red) }
            additions.chunked(2).forEach { pair ->
                pair.forEach { source.onNext("\u001B[31m${it.text}\u001B[0m") }
                cursor.takeNewSpans(log[0]) shouldBe pair
                cursor.takeNewSpans(log[0]) shouldBe emptyList()
            }

            log.lineCount shouldBe 1
            updates.values().drop(1) shouldBe List(additions.size) { LogUpdate(0, 1, 0) }
            LogLineCursor().takeNewSpans(log[0]) shouldBe listOf(LogSpan("prefix ", null)) + additions
            subscription.dispose()
        }

        it("renders queued output before reporting a terminal error on the display scheduler") {
            val source = PublishSubject.create<String>()
            val log = BuildLog()
            val displayScheduler = TestScheduler()
            val failure = IllegalStateException("Log fetch failed")
            val failures = mutableListOf<Pair<Throwable, String>>()
            val subscription = log.render(source, displayScheduler, onError = {
                failures.add(it to log[0].single().text)
            })

            source.onNext("last output")
            source.onError(failure)
            log.lineCount shouldBe 0
            failures shouldBe emptyList()

            displayScheduler.triggerActions()

            failures shouldBe listOf(failure to "last output")
            subscription.dispose()
        }

        it("ignores chunks containing only escapes and resets colors between runs") {
            val log = BuildLog()
            val updates = log.changes.test()

            log.render(Observable.just("\u001B[2K\u001B[31m"), Schedulers.trampoline())
            log.lineCount shouldBe 0
            updates.assertNoValues()

            log.render(Observable.just("\u001B[31mred\u001B[39m plain\u001B[31m"), Schedulers.trampoline())
            log[0] shouldBe listOf(LogSpan("red", 0xFFF00000.toInt()), LogSpan(" plain", null))

            log.clear()
            log.clear()
            log.lineCount shouldBe 0
            updates.assertValues(LogUpdate(null, 0, 1), LogUpdate(null, 0, 0, removedCount = 1))

            log.render(Observable.just("fresh"), Schedulers.trampoline())
            log[0] shouldBe listOf(LogSpan("fresh", null))
        }

        it("continues a partial line and ANSI sequence across chunks") {
            val source = PublishSubject.create<String>()
            val log = BuildLog()
            val subscription = log.render(source, Schedulers.trampoline())

            source.onNext("  <tag>\t\u001B[3")
            source.onNext("1mred\r")
            source.onNext("\n\ncontinued\u001B[0m plain\n")

            val red = 0xFFF00000.toInt()
            log.lineCount shouldBe 4
            log[0] shouldBe listOf(LogSpan("  <tag>\t", null), LogSpan("red", red))
            log[1] shouldBe emptyList()
            log[2] shouldBe listOf(LogSpan("continued", red), LogSpan(" plain", null))
            log[3] shouldBe emptyList()
            subscription.dispose()
        }
    }
})
