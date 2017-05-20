package com.unhappychoice.norimaki.extension

fun String.removeAnsiEscapeCode(): String = replace("\\033\\[\\d+?[A-T]".toRegex(), "")

fun String.replaceAnsiColorCodeToHtml(): String =
    split("(?=\\033\\[(\\d)+?m)".toRegex())
        .map { it.convertAnsiColorCodeToHtmlTag() }
        .joinToString("")

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