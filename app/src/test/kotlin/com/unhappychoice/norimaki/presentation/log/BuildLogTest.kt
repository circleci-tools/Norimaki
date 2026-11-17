package com.unhappychoice.norimaki.presentation.log

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.reactivex.schedulers.Schedulers
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
