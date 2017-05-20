package com.unhappychoice.norimaki.extension

fun String.removeAnsiEscapeCode(): String = replace("\\033\\[[0-9]+?[A-T]".toRegex(), "")

