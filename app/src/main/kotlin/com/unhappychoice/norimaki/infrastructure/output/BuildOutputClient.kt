package com.unhappychoice.norimaki.infrastructure.output

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.unhappychoice.norimaki.extension.AnsiEscapeStream
import io.reactivex.Observable
import io.reactivex.Scheduler
import io.reactivex.schedulers.Schedulers
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import java.io.EOFException
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class BuildOutputClient(
    private val token: String,
    private val client: OkHttpClient = OkHttpClient(),
    private val ioScheduler: Scheduler = Schedulers.io(),
    private val outputByteLimit: Int = 1024 * 1024
) {
    private val rawClient = client.newBuilder().followRedirects(false).build()

    fun fetchOutput(outputUrl: String): Observable<String> = Observable.defer {
        val url = outputUrl.toHttpUrl()
        require(url.isCircleCiOrigin()) { "Action output requests require the CircleCI HTTPS origin" }
        fetchOutput(url, 0)
    }

    private fun fetchOutput(url: HttpUrl, redirects: Int): Observable<String> =
        execute({ rawClient.newCall(Request.Builder().url(url).build()) }) { response ->
            val target = response.rawRedirectTarget()
            when {
                target == null -> {
                    if (!response.isSuccessful) {
                        throw IOException("Action output request failed with HTTP ${response.code}")
                    }
                    Observable.just(completedOutput(response.body))
                }
                !target.isCircleCiOrigin() || redirects == MAX_RAW_REDIRECTS ->
                    throw IOException("Action output redirected to an untrusted host: $target")
                else -> fetchOutput(target, redirects + 1)
            }
        }.flatMap { it }

    fun fetchRawOutput(rawOutputUrl: String, offset: Long): Observable<RawOutputChunk> =
        fetchRawResponse(rawOutputUrl, offset).map { it.chunk }

    fun fetchFinalRawOutput(
        rawOutputUrl: String,
        offset: Long,
        scheduler: Scheduler = Schedulers.io()
    ): Observable<RawOutputChunk> = Observable.defer {
        var nextOffset = offset
        var totalLength = offset
        var complete = false
        Observable.defer {
            fetchRawResponse(rawOutputUrl, nextOffset).map { (chunk, responseLength) ->
                totalLength = maxOf(totalLength, responseLength ?: 0L)
                if (chunk.bytes.isEmpty() && !chunk.limitReached && nextOffset < totalLength) {
                    throw IOException("Raw log response ended before byte $totalLength")
                }
                nextOffset += chunk.bytes.size
                complete = chunk.limitReached || nextOffset >= totalLength
                chunk
            }.retryFinalRead(scheduler)
        }.repeatUntil { complete }
    }

    private fun fetchRawResponse(rawOutputUrl: String, offset: Long): Observable<RawOutputResponse> =
        Observable.defer {
            val url = rawOutputUrl.toHttpUrl()
            require(url.isCircleCiOrigin()) { "Raw log requests require the CircleCI HTTPS origin" }
            val remaining = (outputByteLimit - offset).coerceAtLeast(0)
            if (remaining == 0L) {
                Observable.just(RawOutputResponse(RawOutputChunk(ByteArray(0), limitReached = true)))
            } else {
                fetchRawOutput(url, offset, remaining, 0)
            }
        }.subscribeOn(ioScheduler)

    private fun <T : Any> Observable<T>.retryFinalRead(scheduler: Scheduler): Observable<T> =
        retryWhen { errors ->
            errors.zipWith(Observable.range(1, FINAL_RETRIES + 1)) { error, attempt -> error to attempt }
                .flatMap { (error, attempt) ->
                    if (attempt <= FINAL_RETRIES && isRetryable(error)) {
                        Observable.timer(1, TimeUnit.SECONDS, scheduler)
                    } else {
                        Observable.error<Long>(error)
                    }
                }
        }

    private fun <T : Any> execute(newCall: () -> Call, readBody: (Response) -> T): Observable<T> =
        Observable.create { emitter ->
            val call = newCall()
            val active = AtomicBoolean(true)
            emitter.setCancellable { if (active.getAndSet(false)) call.cancel() }
            if (emitter.isDisposed) return@create

            val output = try {
                call.execute().use(readBody)
            } catch (error: Exception) {
                active.set(false)
                emitter.tryOnError(error)
                return@create
            }
            active.set(false)
            emitter.onNext(output)
            emitter.onComplete()
        }

    private fun fetchRawOutput(
        url: HttpUrl,
        offset: Long,
        remaining: Long,
        redirects: Int
    ): Observable<RawOutputResponse> =
        execute({ rawClient.newCall(rawOutputRequest(url, offset)) }) { response ->
            val target = response.rawRedirectTarget()
            when {
                target == null -> Observable.just(readRawResponse(response, offset, remaining))
                !target.isCircleCiOrigin() || redirects == MAX_RAW_REDIRECTS ->
                    throw RawOutputHttpException(response.code)
                else -> fetchRawOutput(target, offset, remaining, redirects + 1)
            }
        }.flatMap { it }

    private fun Response.rawRedirectTarget(): HttpUrl? =
        if (code in RAW_REDIRECT_CODES) header("Location")?.let(request.url::resolve) else null

    private fun HttpUrl.isCircleCiOrigin(): Boolean = isHttps && host == "circleci.com" && port == 443

    private fun rawOutputRequest(url: HttpUrl, offset: Long): Request =
        Request.Builder()
            .url(url)
            .header("Circle-Token", token)
            .header("Range", "bytes=$offset-")
            .build()

    private fun readRawResponse(response: Response, offset: Long, remaining: Long): RawOutputResponse {
        if (response.code == 206) return readPartialOutput(response, offset, remaining)
        val bytes = when {
            response.isSuccessful -> response.body.readPrefix(remaining, offset)
            response.code == 416 && response.header("Content-Range") == "bytes */$offset" -> ByteArray(0)
            else -> throw RawOutputHttpException(response.code)
        }
        return RawOutputResponse(RawOutputChunk(bytes, limitReached = bytes.size.toLong() == remaining))
    }

    private fun readPartialOutput(response: Response, offset: Long, remaining: Long): RawOutputResponse {
        val range = response.header("Content-Range").orEmpty()
        val values = CONTENT_RANGE.matchEntire(range)?.groupValues
        val start = values?.get(1)?.toLongOrNull()
        val end = values?.get(2)?.toLongOrNull()
        val total = values?.get(3)?.toLongOrNull()
        if (start != offset || end == null || end < offset || end == Long.MAX_VALUE ||
            (values[3] != "*" && (total == null || total <= end))
        ) {
            throw IOException("Invalid Content-Range for raw log offset $offset: $range")
        }
        val bytes = response.body.readPrefix(remaining)
        if (bytes.size.toLong() != minOf(remaining, end - offset + 1)) {
            throw IOException("Invalid raw log body length for Content-Range: $range")
        }
        return RawOutputResponse(RawOutputChunk(bytes, limitReached = bytes.size.toLong() == remaining), total)
    }

    private fun isRetryable(error: Throwable): Boolean = when (error) {
        is RawOutputHttpException -> error.status == 429 || error.status in 500..599
        else -> error is IOException
    }

    private fun completedOutput(body: ResponseBody): String {
        val bytes = body.readPrefix(outputByteLimit.toLong())
        val limitReached = bytes.size == outputByteLimit && body.contentLength() != bytes.size.toLong()
        val log = buildString {
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
        }
        return if (limitReached) AnsiEscapeStream().strip(log) + OUTPUT_LIMIT_MESSAGE else log
    }

    private companion object {
        const val FINAL_RETRIES = 2
        const val MAX_RAW_REDIRECTS = 20
        val RAW_REDIRECT_CODES = setOf(300, 301, 302, 303, 307, 308)
        val CONTENT_RANGE = Regex("""bytes ([0-9]+)-([0-9]+)/([0-9]+|\*)""", RegexOption.IGNORE_CASE)
    }
}

private data class RawOutputResponse(val chunk: RawOutputChunk, val totalLength: Long? = null)

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
