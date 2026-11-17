package com.unhappychoice.norimaki.extension

import io.reactivex.Observable
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction

fun Observable<ByteArray>.decodeUtf8(): Observable<String> =
    Observable.defer {
        val decoder = Utf8StreamDecoder()
        map { decoder.decode(it) }
            .concatWith(Observable.fromCallable { decoder.decode(byteArrayOf(), endOfInput = true) })
            .filter { it.isNotEmpty() }
    }

private class Utf8StreamDecoder {
    private val decoder = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPLACE)
    private var pending = byteArrayOf()

    fun decode(bytes: ByteArray, endOfInput: Boolean = false): String {
        val input = ByteBuffer.wrap(if (pending.isEmpty()) bytes else pending + bytes)
        val output = CharBuffer.allocate(input.remaining())
        decoder.decode(input, output, endOfInput)
        if (endOfInput) decoder.flush(output)
        pending = ByteArray(input.remaining()).also { input.get(it) }
        return String(output.array(), 0, output.position())
    }
}
