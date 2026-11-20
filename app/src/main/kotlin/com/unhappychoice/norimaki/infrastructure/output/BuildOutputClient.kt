package com.unhappychoice.norimaki.infrastructure.output

import com.unhappychoice.norimaki.extension.asSequence
import io.reactivex.Observable
import io.reactivex.Scheduler
import io.reactivex.schedulers.Schedulers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class BuildOutputClient(
    private val token: String,
    private val client: OkHttpClient = OkHttpClient(),
    private val ioScheduler: Scheduler = Schedulers.io()
) {
    fun fetchOutput(outputUrl: String): Observable<String> =
        execute({ Request.Builder().url(outputUrl).build() }) { response ->
            if (!response.isSuccessful) throw IOException("Action output request failed with HTTP ${response.code}")
            joinMessages(response.body.string())
        }

    fun fetchRawOutput(rawOutputUrl: String, offset: Long): Observable<ByteArray> =
        execute({ rawOutputRequest(rawOutputUrl, offset) }) { rawOutputBytes(it, offset) }
            .subscribeOn(ioScheduler)

    fun fetchFinalRawOutput(
        rawOutputUrl: String,
        offset: Long,
        scheduler: Scheduler = Schedulers.io()
    ): Observable<ByteArray> =
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

    private fun <T : Any> execute(request: () -> Request, readBody: (Response) -> T): Observable<T> =
        Observable.create { emitter ->
            val call = client.newCall(request())
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

    private fun rawOutputRequest(url: String, offset: Long): Request =
        Request.Builder()
            .url(url)
            .header("Circle-Token", token)
            .header("Range", "bytes=$offset-")
            .build()

    private fun rawOutputBytes(response: Response, offset: Long): ByteArray = when {
        response.code == 206 -> partialOutputBytes(response, offset)
        response.isSuccessful -> response.body.bytes().let { bytes ->
            when {
                offset == 0L -> bytes
                offset >= bytes.size -> ByteArray(0)
                else -> bytes.copyOfRange(offset.toInt(), bytes.size)
            }
        }
        response.code == 416 && response.header("Content-Range") == "bytes */$offset" -> ByteArray(0)
        else -> throw RawOutputHttpException(response.code)
    }

    private fun partialOutputBytes(response: Response, offset: Long): ByteArray {
        val range = response.header("Content-Range").orEmpty()
        val start = CONTENT_RANGE.matchEntire(range)?.groupValues?.get(1)?.toLongOrNull()
        if (start != offset) throw IOException("Invalid Content-Range for raw log offset $offset: $range")
        return response.body.bytes()
    }

    private fun isRetryable(error: Throwable): Boolean = when (error) {
        is RawOutputHttpException -> error.status == 429 || error.status in 500..599
        else -> error is IOException
    }

    private fun joinMessages(json: String): String =
        JSONArray(json).asSequence()
            .map { it.getString("message") }
            .joinToString("")

    private companion object {
        const val FINAL_RETRIES = 2
        val CONTENT_RANGE = Regex("""bytes ([0-9]+)-[0-9]+/(?:[0-9]+|\*)""", RegexOption.IGNORE_CASE)
    }
}

internal class RawOutputHttpException(val status: Int) : IOException("Raw log request failed with HTTP $status")
