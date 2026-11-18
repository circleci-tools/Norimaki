package com.unhappychoice.norimaki.extension

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction

internal class Utf8StreamDecoder {
    private val decoder = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPLACE)
    private var pending = byteArrayOf()

    fun decode(bytes: ByteArray): String = decode(bytes, endOfInput = false)

    fun finish(): String = decode(byteArrayOf(), endOfInput = true)

    private fun decode(bytes: ByteArray, endOfInput: Boolean): String {
        val input = ByteBuffer.wrap(if (pending.isEmpty()) bytes else pending + bytes)
        val output = CharBuffer.allocate(input.remaining())
        decoder.decode(input, output, endOfInput)
        if (endOfInput) decoder.flush(output)
        pending = ByteArray(input.remaining()).also { input.get(it) }
        return String(output.array(), 0, output.position())
    }
}
