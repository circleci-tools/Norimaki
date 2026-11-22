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

private val escapeIntroducers = charArrayOf('\u001B', *('\u0080'..'\u009F').toList().toCharArray())
private const val controlStringEnd = """\x1B\\|[\x18\x1A\x9C]"""
private val ansiEscape = Regex(
    """(?:\x1B\]|\x9D)[^\x07\x18\x1A\x1B\x9C]*+(?:\x07|$controlStringEnd)""" +
        """|(?:\x1B[PX^_]|[\x90\x98\x9E\x9F])[^\x18\x1A\x1B\x9C]*+(?:$controlStringEnd)""" +
        """|\x1B\[([0-9;]*m)|(?:\x1B\[|\x9B)[0-?]*[ -/]*[@-~]""" +
        """|\x1B(?![PX\[\]^_])[ -/]*[0-~]|(?![\x90\x98\x9B\x9D-\x9F])[\x80-\x9F]"""
)

internal fun ansiForegroundColor(code: String?): String? =
    when (code) {
        "30" -> "#111111"
        "31" -> "#F00000"
        "32" -> "#00F000"
        "33" -> "#F0F000"
        "34" -> "#0000F0"
        "35" -> "#F000F0"
        "36" -> "#00F0F0"
        "37", "39" -> "#F0F0F0"
        else -> null
    }

private fun String.convertAnsiColorCodeToHtmlTag(): String {
    val color = ansiForegroundColor("\\033\\[(\\d+?)m".toRegex().find(this)?.groupValues?.lastOrNull())
    val text = replace("\\033\\[\\d+?m".toRegex(), "")
    return color?.let { "<font color=$it>$text</font>" } ?: text
}
