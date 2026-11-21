package com.unhappychoice.norimaki.domain.service

import io.reactivex.Single
import io.reactivex.SingleEmitter
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.OutputStream

internal class ArtifactDownloader(client: OkHttpClient = OkHttpClient()) {
    private val http = client.newBuilder().followRedirects(false).followSslRedirects(false).build()

    fun download(
        url: String,
        token: String,
        onFailure: () -> Unit = {},
        openDestination: () -> OutputStream
    ): Single<Unit> = Single.create { emitter ->
        try {
            val source = url.toHttpUrlOrNull()?.takeIf(::isTrusted)
                ?: throw IOException("Invalid CircleCI artifact URL")
            copy(source, token, openDestination, emitter)
            if (emitter.isDisposed) onFailure() else emitter.onSuccess(Unit)
        } catch (error: Exception) {
            onFailure()
            emitter.tryOnError(error)
        }
    }

    private fun copy(
        source: HttpUrl,
        token: String,
        openDestination: () -> OutputStream,
        emitter: SingleEmitter<Unit>
    ) {
        var url = source
        var sendToken = true
        repeat(11) {
            val call = http.newCall(request(url, token.takeIf { sendToken && isTrusted(url) }))
            emitter.setCancellable(call::cancel)
            call.execute().use { response ->
                if (emitter.isDisposed) throw IOException("Artifact download cancelled")
                if (response.isSuccessful) {
                    response.body.byteStream().use { input ->
                        openDestination().use { output -> input.copyTo(output) }
                    }
                    return
                }
                if (response.code !in redirectCodes) throw IOException("Artifact download failed")
                url = response.header("Location")?.let(url::resolve)
                    ?: throw IOException("Invalid artifact redirect")
                sendToken = sendToken && isTrusted(url)
            }
        }
        throw IOException("Too many artifact redirects")
    }

    private fun request(url: HttpUrl, token: String?): Request {
        if (!url.isHttps || url.username.isNotEmpty() || url.password.isNotEmpty()) {
            throw IOException("Invalid artifact download destination")
        }
        return Request.Builder().url(url).apply {
            token?.let { header("Circle-Token", it) }
        }.build()
    }

    private fun isTrusted(url: HttpUrl): Boolean =
        url.isHttps && url.port == 443 &&
            (url.host == "circleci.com" || url.host == "dl.circleci.com" ||
                url.host.endsWith(".circle-artifacts.com"))

    private companion object {
        val redirectCodes = setOf(301, 302, 303, 307, 308)
    }
}
