package com.unhappychoice.norimaki.presentation.view

import io.reactivex.Observable
import java.util.regex.Pattern

internal class LogMatchIndex private constructor(private val offsets: LongArray, val size: Int) {
    operator fun get(index: Int): IntRange {
        val offset = offsets[index]
        return (offset ushr 32).toInt() until offset.toInt()
    }

    companion object {
        val EMPTY = LogMatchIndex(LongArray(0), 0)

        fun find(text: CharSequence, query: String): Observable<LogMatchIndex> = Observable.create { emitter ->
            val matcher = Pattern.compile(Pattern.quote(query), Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE)
                .matcher(text.toString())
            var offsets = EMPTY.offsets
            var size = 0
            while (!emitter.isDisposed && matcher.find()) {
                if (size == offsets.size) offsets = offsets.copyOf(maxOf(16, size * 2))
                // Pack the start and exclusive end into the high and low halves.
                offsets[size++] = (matcher.start().toLong() shl 32) or matcher.end().toLong()
            }
            if (!emitter.isDisposed) {
                emitter.onNext(if (size == 0) EMPTY else LogMatchIndex(offsets, size))
                emitter.onComplete()
            }
        }
    }
}
