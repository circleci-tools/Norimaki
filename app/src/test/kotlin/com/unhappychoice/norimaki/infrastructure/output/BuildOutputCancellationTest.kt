package com.unhappychoice.norimaki.infrastructure.output

import com.sun.net.httpserver.HttpServer
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.SECONDS

class BuildOutputCancellationTest : DescribeSpec({
    it("cancels a completed log download during a response body read") {
        verifyDownloadCancellation("""[{"message":"completed log"}]""") { client, url ->
            client.fetchOutput(url).subscribeOn(Schedulers.io())
        }
    }

    it("cancels a live log download during a response body read after a same-origin redirect") {
        verifyDownloadCancellation("live log", redirect = true) { client, url ->
            client.fetchRawOutput(url, 0)
        }
    }
})

private fun verifyDownloadCancellation(
    body: String,
    redirect: Boolean = false,
    fetch: (BuildOutputClient, String) -> Observable<*>
) {
    val firstByteSent = CountDownLatch(1)
    val releaseRemainingBody = CountDownLatch(1)
    val bodyReadStarted = CountDownLatch(1)
    val callCanceled = CountDownLatch(1)
    val canceledCallFailed = CountDownLatch(1)
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/log") { exchange ->
        val bytes = body.toByteArray()
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { output ->
            output.write(bytes, 0, 1)
            output.flush()
            firstByteSent.countDown()
            releaseRemainingBody.await()
            output.write(bytes, 1, bytes.size - 1)
        }
    }
    server.start()
    val localUrl = "http://127.0.0.1:${server.address.port}/log".toHttpUrl()
    val httpClient = OkHttpClient.Builder().readTimeout(30, SECONDS).addInterceptor { chain ->
        val request = chain.request()
        if (request.url.encodedPath == "/redirect") {
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(302)
                .message("Found")
                .header("Location", "/log")
                .body("".toResponseBody())
                .build()
        } else {
            chain.proceed(request.newBuilder().url(localUrl).build())
        }
    }.eventListener(object : EventListener() {
        override fun responseBodyStart(call: Call) {
            bodyReadStarted.countDown()
        }

        override fun canceled(call: Call) {
            callCanceled.countDown()
        }

        override fun callFailed(call: Call, ioe: IOException) {
            if (call.isCanceled()) canceledCallFailed.countDown()
        }
    }).build()
    val url = if (redirect) "https://circleci.com/redirect" else "https://circleci.com/log"
    val observer = fetch(BuildOutputClient("token", httpClient), url).test()

    try {
        firstByteSent.await(5, SECONDS) shouldBe true
        bodyReadStarted.await(5, SECONDS) shouldBe true
        observer.dispose()

        callCanceled.await(5, SECONDS) shouldBe true
        canceledCallFailed.await(5, SECONDS) shouldBe true
        observer.assertEmpty()
    } finally {
        observer.dispose()
        releaseRemainingBody.countDown()
        server.stop(0)
        httpClient.connectionPool.evictAll()
    }
}
