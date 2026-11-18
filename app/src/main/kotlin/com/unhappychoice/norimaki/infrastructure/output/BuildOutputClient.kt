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

class BuildOutputClient(private val token: String, private val client: OkHttpClient = OkHttpClient()) {
    fun fetchOutput(outputUrl: String): Observable<String> =
        Observable.fromCallable { execute(Request.Builder().url(outputUrl).build()).use { it.body.string() } }
            .map { joinMessages(it) }

    fun fetchRawOutput(rawOutputUrl: String, offset: Long): Observable<ByteArray> =
        Observable.fromCallable {
            execute(rawOutputRequest(rawOutputUrl, offset)).use { rawOutputBytes(it, offset) }
        }

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

    private fun execute(request: Request): Response = client.newCall(request).execute()

    private fun rawOutputRequest(url: String, offset: Long): Request =
        Request.Builder()
            .url(url)
            .header("Circle-Token", token)
            .header("Range", "bytes=$offset-")
            .build()

    private fun rawOutputBytes(response: Response, offset: Long): ByteArray = when {
        response.isSuccessful -> response.body.bytes()
        response.code == 416 && response.header("Content-Range") == "bytes */$offset" -> ByteArray(0)
        else -> throw RawOutputHttpException(response.code)
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
    }
}

internal class RawOutputHttpException(val status: Int) : IOException("Raw log request failed with HTTP $status")
