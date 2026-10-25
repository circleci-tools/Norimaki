package com.unhappychoice.norimaki.infrastructure.output

import com.unhappychoice.norimaki.extension.asSequence
import io.reactivex.Observable
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray

class BuildOutputClient(private val token: String, private val client: OkHttpClient = OkHttpClient()) {
    fun fetchOutput(outputUrl: String): Observable<String> =
        Observable.fromCallable { execute(Request.Builder().url(outputUrl).build()).use { it.body.string() } }
            .map { joinMessages(it) }

    fun fetchRawOutput(rawOutputUrl: String, offset: Long): Observable<ByteArray> =
        Observable.fromCallable { execute(rawOutputRequest(rawOutputUrl, offset)).use { rawOutputBytes(it) } }

    private fun execute(request: Request): Response = client.newCall(request).execute()

    private fun rawOutputRequest(url: String, offset: Long): Request =
        Request.Builder()
            .url(url)
            .header("Circle-Token", token)
            .header("Range", "bytes=$offset-")
            .build()

    private fun rawOutputBytes(response: Response): ByteArray =
        newRawOutput(response.isSuccessful, response.body.bytes())

    private fun joinMessages(json: String): String =
        JSONArray(json).asSequence()
            .map { it.getString("message") }
            .joinToString("")
}

fun newRawOutput(isSuccessful: Boolean, body: ByteArray): ByteArray =
    if (isSuccessful) body else ByteArray(0)
