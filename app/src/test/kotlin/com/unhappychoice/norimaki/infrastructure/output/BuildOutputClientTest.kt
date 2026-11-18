package com.unhappychoice.norimaki.infrastructure.output

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.reactivex.schedulers.TestScheduler
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class BuildOutputClientTest : DescribeSpec({
    val url = "https://example.com/log"
    val offset = 42L

    describe("fetchFinalRawOutput()") {
        listOf("HTTP 503", "I/O error").forEach { failure ->
            it("should emit the remaining bytes once at the same offset after an initial $failure") {
                val scheduler = TestScheduler()
                val ranges = mutableListOf<String?>()
                val client = outputClient { request ->
                    ranges.add(request.header("Range"))
                    when {
                        ranges.size > 1 -> response(request, 206, "last lines")
                        failure == "HTTP 503" -> response(request, 503, "Unavailable")
                        else -> throw IOException("Connection lost")
                    }
                }
                val observer = client.fetchFinalRawOutput(url, offset, scheduler).map { String(it) }.test()

                observer.assertEmpty()
                scheduler.advanceTimeBy(5, TimeUnit.SECONDS)

                observer.assertResult("last lines")
                ranges shouldBe listOf("bytes=42-", "bytes=42-")
            }
        }

        it("should report persistent failure after two retries") {
            val scheduler = TestScheduler()
            var requests = 0
            val client = outputClient { request ->
                requests += 1
                response(request, 503)
            }
            val observer = client.fetchFinalRawOutput(url, offset, scheduler).test()

            scheduler.advanceTimeBy(5, TimeUnit.SECONDS)

            observer.assertFailure(RawOutputHttpException::class.java)
            requests shouldBe 3
        }

        it("should report a permanent HTTP failure immediately") {
            val client = outputClient { response(it, 403) }

            client.fetchFinalRawOutput(url, offset, TestScheduler()).test()
                .assertFailure(RawOutputHttpException::class.java)
        }

        it("should accept an empty successful response") {
            val client = outputClient { response(it, 204) }

            client.fetchFinalRawOutput(url, offset, TestScheduler()).map { it.size }.test().assertResult(0)
        }

        it("should accept a range response confirming that the entire log was already read") {
            val client = outputClient {
                response(it, 416).newBuilder().header("Content-Range", "bytes */42").build()
            }

            client.fetchFinalRawOutput(url, offset, TestScheduler()).map { it.size }.test().assertResult(0)
        }
    }
})

private fun outputClient(respond: (Request) -> Response): BuildOutputClient =
    BuildOutputClient("token", OkHttpClient.Builder().addInterceptor { respond(it.request()) }.build())

private fun response(request: Request, status: Int, body: String = ""): Response =
    Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(status)
        .message("Test response")
        .body(body.toResponseBody())
        .build()
