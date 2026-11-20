package com.unhappychoice.norimaki.extension

fun String.removeAnsiEscapeCode(): String = AnsiEscapeStream().strip(this)

fun String.replaceAnsiColorCodeToHtml(): String =
    replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .split("(?=\\033\\[(\\d)+?m)".toRegex())
        .map { it.convertAnsiColorCodeToHtmlTag() }
        .joinToString("")
        .replace("\n", "<br>")

internal class AnsiEscapeStream {
    private var pending = ""

    fun strip(chunk: String): String {
        val input = pending + chunk
        pending = ""
        val matcher = ansiEscape.toPattern().matcher(input)
        return buildString {
            var start = 0
            while (start < input.length) {
                val escape = input.indexOfAny(escapeIntroducers, start)
                if (escape == -1) {
                    append(input, start, input.length)
                    break
                }

                append(input, start, escape)
                matcher.region(escape, input.length)
                when {
                    matcher.lookingAt() -> {
                        if (matcher.group(1) != null) append(input, escape, matcher.end())
                        start = matcher.end()
                    }
                    matcher.hitEnd() -> {
                        pending = input.substring(escape)
                        break
                    }
                    else -> {
                        append(input[escape])
                        start = escape + 1
                    }
                }
            }
        }
    }
}

private val escapeIntroducers = charArrayOf('\u001B', '\u009D')
private val ansiEscape = Regex(
    """(?:\x1B\]|\x9D)8;[^;\p{Cc}]*;[^\p{Cc}]*(?:\x07|\x1B\\|\x9C)|\x1B\[(?:(\d+m)|[0-?]*[ -/]*[@-~])"""
)

private fun String.convertAnsiColorCodeToHtmlTag(): String =
    when ("\\033\\[(\\d+?)m".toRegex().find(this)?.groupValues?.lastOrNull()) {
        "30" -> "<font color=#111111>" + replace("\\033\\[\\d+?m".toRegex(), "") + "</font>"
        "31" -> "<font color=#F00000>" + replace("\\033\\[\\d+?m".toRegex(), "") + "</font>"
        "32" -> "<font color=#00F000>" + replace("\\033\\[\\d+?m".toRegex(), "") + "</font>"
        "33" -> "<font color=#F0F000>" + replace("\\033\\[\\d+?m".toRegex(), "") + "</font>"
        "34" -> "<font color=#0000F0>" + replace("\\033\\[\\d+?m".toRegex(), "") + "</font>"
        "35" -> "<font color=#F000F0>" + replace("\\033\\[\\d+?m".toRegex(), "") + "</font>"
        "36" -> "<font color=#00F0F0>" + replace("\\033\\[\\d+?m".toRegex(), "") + "</font>"
        "37" -> "<font color=#F0F0F0>" + replace("\\033\\[\\d+?m".toRegex(), "") + "</font>"
        "39" -> "<font color=#F0F0F0>" + replace("\\033\\[\\d+?m".toRegex(), "") + "</font>"
        else -> replace("\\033\\[\\d+?m".toRegex(), "")
    }
