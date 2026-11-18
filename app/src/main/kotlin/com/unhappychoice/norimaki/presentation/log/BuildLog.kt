package com.unhappychoice.norimaki.presentation.log

import io.reactivex.Observable
import io.reactivex.Scheduler
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.subjects.PublishSubject

internal data class LogUpdate(val changedLine: Int?, val insertStart: Int, val insertCount: Int)

internal class BuildLog {
    private val lines = mutableListOf<MutableList<LogSpan>>()
    private val updates = PublishSubject.create<LogUpdate>()

    val changes = updates.hide()
    val lineCount: Int get() = lines.size

    operator fun get(index: Int): List<LogSpan> = lines[index]

    fun render(
        chunks: Observable<String>,
        displayScheduler: Scheduler,
        onError: (Throwable) -> Unit = { it.printStackTrace() },
        format: (String) -> List<List<LogSpan>> = LogChunkFormatter()::format
    ): Disposable =
        chunks.map(format)
            .observeOn(displayScheduler, true)
            .subscribeBy(onNext = ::append, onError = onError)

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
