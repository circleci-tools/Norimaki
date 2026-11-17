package com.unhappychoice.norimaki.presentation.log

import com.unhappychoice.norimaki.extension.ansiForegroundColor

internal data class LogSpan(val text: String, val color: Int?)

internal class LogChunkFormatter {
    private var color: Int? = null
    private var pendingEscape = ""

    fun format(chunk: String): List<List<LogSpan>> {
        val input = pendingEscape + chunk
        pendingEscape = ""
        val lines = mutableListOf(mutableListOf<LogSpan>())
        var start = 0
        tokens.findAll(input).forEach { token ->
            input.addSpan(lines.last(), start, token.range.first)
            when (token.value) {
                "\n" -> lines.add(mutableListOf())
                "\r" -> Unit
                else -> applyEscape(token.value)
            }
            start = token.range.last + 1
        }
        input.addSpan(lines.last(), start, input.length)
        return lines
    }

    private fun String.addSpan(line: MutableList<LogSpan>, start: Int, end: Int) {
        if (start < end) line.add(LogSpan(substring(start, end), color))
    }

    private fun applyEscape(escape: String) {
        if (escape.endsWith('m')) {
            escape.substring(2, escape.lastIndex).split(';').forEach { code ->
                when (code) {
                    "", "0" -> color = null
                    "39" -> color = null
                    else -> ansiForegroundColor(code)?.let { color = it.substring(1).toInt(16) or 0xFF000000.toInt() }
                }
            }
        } else if (incompleteEscape.matches(escape)) {
            pendingEscape = escape
        }
    }

    private companion object {
        val incompleteEscape = Regex("\u001B(?:\\[[0-?]*[ -/]*)?")
        val tokens = Regex("[\\r\\n]|\u001B\\[[0-?]*[ -/]*[@-~]|${incompleteEscape.pattern}\\z")
    }
}
