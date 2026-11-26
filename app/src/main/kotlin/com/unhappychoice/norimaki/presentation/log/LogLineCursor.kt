package com.unhappychoice.norimaki.presentation.log

internal class LogLineCursor {
    private var spanCount = 0

    fun takeNewSpans(line: List<LogSpan>): List<LogSpan> {
        val spans = line.subList(spanCount, line.size)
        spanCount = line.size
        return spans
    }
}
