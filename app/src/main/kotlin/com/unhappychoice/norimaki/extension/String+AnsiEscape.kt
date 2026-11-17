package com.unhappychoice.norimaki.extension

fun String.removeAnsiEscapeCode(): String = replace("\\033\\[\\d+?[A-T]".toRegex(), "")

fun String.replaceAnsiColorCodeToHtml(): String =
    split("(?=\\033\\[(\\d)+?m)".toRegex())
        .map { it.convertAnsiColorCodeToHtmlTag() }
        .joinToString("")

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
