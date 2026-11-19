package com.unhappychoice.norimaki.infrastructure.output

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import io.reactivex.Observable
import io.reactivex.Scheduler
import io.reactivex.schedulers.Schedulers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import java.io.EOFException
import java.io.IOException
import java.util.concurrent.TimeUnit

class BuildOutputClient(
    private val token: String,
    private val client: OkHttpClient = OkHttpClient(),
    private val ioScheduler: Scheduler = Schedulers.io(),
    private val outputByteLimit: Int = 1024 * 1024
) {
    fun fetchOutput(outputUrl: String): Observable<String> =
        Observable.fromCallable {
            execute(Request.Builder().url(outputUrl).build()).use { response ->
                if (!response.isSuccessful) throw IOException("Action output request failed with HTTP ${response.code}")
                completedOutput(response.body)
            }
        }

    fun fetchRawOutput(rawOutputUrl: String, offset: Long): Observable<RawOutputChunk> =
        Observable.fromCallable {
            val remaining = (outputByteLimit - offset).coerceAtLeast(0)
            if (remaining == 0L) RawOutputChunk(ByteArray(0), limitReached = true)
            else execute(rawOutputRequest(rawOutputUrl, offset)).use { rawOutputChunk(it, offset, remaining) }
        }.subscribeOn(ioScheduler)

    fun fetchFinalRawOutput(
        rawOutputUrl: String,
        offset: Long,
        scheduler: Scheduler = Schedulers.io()
    ): Observable<RawOutputChunk> =
        fetchRawOutput(rawOutputUrl, offset).retryWhen { errors ->
            errors.zipWith(Observable.range(1, FINAL_RETRIES + 1)) { error, attempt -> error to attempt }
                .flatMap { (error, attempt) ->
                    if (attempt <= FINAL_RETRIES && isRetryable(error)) {
                        Observable.timer(1, TimeUnit.SECONDS, scheduler)
                    } else {
                        Observable.error<Long>(error)
                    }
                }
        }

    private fun execute(request: Request): Response = client.newCall(request).execute()

    private fun rawOutputRequest(url: String, offset: Long): Request =
        Request.Builder()
            .url(url)
            .header("Circle-Token", token)
            .header("Range", "bytes=$offset-")
            .build()

    private fun rawOutputChunk(response: Response, offset: Long, remaining: Long): RawOutputChunk {
        val bytes = when {
            response.code == 206 -> {
                validatePartialOutput(response, offset)
                response.body.readPrefix(remaining)
            }
            response.isSuccessful -> response.body.readPrefix(remaining, offset)
            response.code == 416 && response.header("Content-Range") == "bytes */$offset" -> ByteArray(0)
            else -> throw RawOutputHttpException(response.code)
        }
        return RawOutputChunk(bytes, limitReached = bytes.size.toLong() == remaining)
    }

    private fun validatePartialOutput(response: Response, offset: Long) {
        val range = response.header("Content-Range").orEmpty()
        val start = CONTENT_RANGE.matchEntire(range)?.groupValues?.get(1)?.toLongOrNull()
        if (start != offset) throw IOException("Invalid Content-Range for raw log offset $offset: $range")
    }

    private fun isRetryable(error: Throwable): Boolean = when (error) {
        is RawOutputHttpException -> error.status == 429 || error.status in 500..599
        else -> error is IOException
    }

    private fun completedOutput(body: ResponseBody): String {
        val bytes = body.readPrefix(outputByteLimit.toLong())
        val limitReached = bytes.size == outputByteLimit && body.contentLength() != bytes.size.toLong()
        return buildString {
            JsonReader(bytes.toResponseBody(body.contentType()).charStream()).use { reader ->
                try {
                    reader.beginArray()
                    while (reader.hasNext()) append(reader.readMessage())
                    reader.endArray()
                    check(reader.peek() == JsonToken.END_DOCUMENT) { "Unexpected content after action output" }
                } catch (error: IOException) {
                    if (!limitReached) throw error
                }
            }
            if (limitReached) append(OUTPUT_LIMIT_MESSAGE)
        }
    }

    private companion object {
        const val FINAL_RETRIES = 2
        val CONTENT_RANGE = Regex("""bytes ([0-9]+)-[0-9]+/(?:[0-9]+|\*)""", RegexOption.IGNORE_CASE)
    }
}

internal class RawOutputHttpException(val status: Int) : IOException("Raw log request failed with HTTP $status")

data class RawOutputChunk(val bytes: ByteArray, val limitReached: Boolean = false)

internal const val OUTPUT_LIMIT_MESSAGE = "\n[Action output limit reached.]\n"

private fun ResponseBody.readPrefix(limit: Long, offset: Long = 0): ByteArray {
    val source = source()
    try {
        source.skip(offset)
    } catch (_: EOFException) {
        return ByteArray(0)
    }
    val prefix = Buffer()
    while (prefix.size < limit) {
        if (source.read(prefix, limit - prefix.size) == -1L) break
    }
    return prefix.readByteArray()
}

private fun JsonReader.readMessage(): String {
    var message: String? = null
    beginObject()
    while (hasNext()) {
        if (nextName() == "message") message = nextString() else skipValue()
    }
    endObject()
    return requireNotNull(message) { "Missing action output message" }
}
