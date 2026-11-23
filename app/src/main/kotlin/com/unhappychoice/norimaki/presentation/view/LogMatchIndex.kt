package com.unhappychoice.norimaki.presentation.view

import com.unhappychoice.norimaki.presentation.log.LogMatch
import com.unhappychoice.norimaki.presentation.log.LogSpan
import io.reactivex.Observable
import java.util.regex.Pattern

internal class LogMatchIndex private constructor(
    private val lineNumbers: IntArray,
    private val offsets: LongArray,
    val size: Int
) {
    operator fun get(index: Int): LogMatch {
        val offset = offsets[index]
        return LogMatch(lineNumbers[index], (offset ushr 32).toInt(), offset.toInt())
    }

    companion object {
        val EMPTY = LogMatchIndex(IntArray(0), LongArray(0), 0)

        fun find(lines: List<List<LogSpan>>, query: String): Observable<LogMatchIndex> = Observable.create { emitter ->
            val pattern = Pattern.compile(Pattern.quote(query), Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE)
            var lineNumbers = EMPTY.lineNumbers
            var offsets = EMPTY.offsets
            var size = 0
            lines.forEachIndexed { line, spans ->
                if (emitter.isDisposed) return@create
                val matcher = pattern.matcher(spans.joinToString("") { it.text })
                while (!emitter.isDisposed && matcher.find()) {
                    if (size == offsets.size) {
                        val capacity = maxOf(16, size * 2)
                        lineNumbers = lineNumbers.copyOf(capacity)
                        offsets = offsets.copyOf(capacity)
                    }
                    lineNumbers[size] = line
                    // Pack the start and exclusive end into the high and low halves.
                    offsets[size++] = (matcher.start().toLong() shl 32) or matcher.end().toLong()
                }
            }
            if (!emitter.isDisposed) {
                emitter.onNext(if (size == 0) EMPTY else LogMatchIndex(lineNumbers, offsets, size))
                emitter.onComplete()
            }
        }
    }
}
