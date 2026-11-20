package com.unhappychoice.norimaki.extension

fun String.removeAnsiEscapeCode(): String =
    replace(osc8Hyperlink, "").replace("\\033\\[(?!\\d+m)[0-?]*[ -/]*[@-~]".toRegex(), "")

fun String.replaceAnsiColorCodeToHtml(): String =
    replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .split("(?=\\033\\[(\\d)+?m)".toRegex())
        .map { it.convertAnsiColorCodeToHtmlTag() }
        .joinToString("")
        .replace("\n", "<br>")

private val osc8Hyperlink = Regex("""(?:\x1B\]|\x9D)8;[^;\p{Cc}]*;[^\p{Cc}]*(?:\x07|\x1B\\|\x9C)""")

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
