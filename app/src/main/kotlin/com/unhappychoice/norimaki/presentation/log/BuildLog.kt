package com.unhappychoice.norimaki.presentation.log

import io.reactivex.Observable
import io.reactivex.Scheduler
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.subjects.PublishSubject

internal data class LogUpdate(val changedLine: Int?, val insertStart: Int, val insertCount: Int, val removedCount: Int = 0)

internal data class LogMatch(val line: Int, val start: Int, val end: Int)

internal class BuildLog {
    private val lines = mutableListOf<MutableList<LogSpan>>()
    private val updates = PublishSubject.create<LogUpdate>()

    val changes = updates.hide()
    val lineCount: Int get() = lines.size

    operator fun get(index: Int): List<LogSpan> = lines[index]

    fun snapshot(): List<List<LogSpan>> =
        lines.mapIndexed { index, line -> if (index == lines.lastIndex) line.toList() else line }

    fun render(
        chunks: Observable<String>,
        displayScheduler: Scheduler,
        onError: (Throwable) -> Unit = { it.printStackTrace() },
        onComplete: () -> Unit = {},
        format: (String) -> List<List<LogSpan>> = LogChunkFormatter()::format
    ): Disposable =
        chunks.map(format)
            .observeOn(displayScheduler, true)
            .subscribeBy(onNext = ::append, onError = onError, onComplete = onComplete)

    fun clear() {
        val removedCount = lineCount
        if (removedCount == 0) return
        lines.clear()
        updates.onNext(LogUpdate(null, 0, 0, removedCount))
    }

    private fun append(chunk: List<List<LogSpan>>) {
        if (chunk.size == 1 && chunk.first().isEmpty()) return

        val previousCount = lineCount
        val firstLine = chunk.first()
        val changedLine = (previousCount - 1).takeIf { previousCount > 0 && firstLine.isNotEmpty() }

        if (previousCount == 0) lines.add(firstLine.toMutableList())
        else lines.last().addAll(firstLine)
        chunk.asSequence().drop(1).forEach { lines.add(it.toMutableList()) }

        updates.onNext(LogUpdate(changedLine, previousCount, lineCount - previousCount))
    }
}
