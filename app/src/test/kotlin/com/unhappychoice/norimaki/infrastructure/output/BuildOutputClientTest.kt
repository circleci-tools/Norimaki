package com.unhappychoice.norimaki.infrastructure.output

import com.unhappychoice.norimaki.extension.polling
import com.unhappychoice.norimaki.presentation.presenter.pollActionOutputs
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers
import io.reactivex.schedulers.TestScheduler
import io.reactivex.subjects.PublishSubject
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.Source
import okio.Timeout
import okio.buffer
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicInteger

class BuildOutputClientTest : DescribeSpec({
    val url = "https://example.com/log"
    val offset = 42L

    listOf(true, false).forEach { declaredLength ->
        describe("log reads with declared length = $declaredLength") {
            it("keeps complete messages from an oversized completed log and stops at the byte limit") {
                val prefix = """[{"message":"first line"},{"message":"partial"""
                val body = TrackedBody(prefix + """ message"}]""", declaredLength)
                val client = outputClient(prefix.toByteArray().size) { request ->
                    response(request, 200).newBuilder().body(body).build()
                }

                client.fetchOutput(url).test().assertResult("first line" + OUTPUT_LIMIT_MESSAGE)
                body.bytesRead shouldBe prefix.toByteArray().size.toLong()
                body.closed shouldBe true
            }

            it("keeps live bytes up to the remaining budget and closes the response") {
                val bodies = List(2) { TrackedBody("é and more", declaredLength) }
                val pending = bodies.iterator()
                val client = outputClient(5) { request ->
                    request.header("Range") shouldBe "bytes=3-"
                    response(request, 206, contentRange = "bytes 3-13/*").newBuilder().body(pending.next()).build()
                }

                listOf(client.fetchRawOutput(url, 3), client.fetchFinalRawOutput(url, 3, TestScheduler()))
                    .forEach { output ->
                        output.test()
                            .assertValue { it.bytes.decodeToString() == "é" && it.limitReached }
                            .assertComplete().assertNoErrors()
                    }
                bodies.forEach { body ->
                    body.bytesRead shouldBe 2L
                    body.closed shouldBe true
                }
                client.fetchRawOutput(url, 5).test()
                    .assertValue { it.bytes.isEmpty() && it.limitReached }
                    .assertComplete().assertNoErrors()
            }

            it("skips previously read bytes when a full response exceeds the remaining budget") {
                val body = TrackedBody("猫log and more", declaredLength)
                val client = outputClient(6) { request ->
                    response(request, 200).newBuilder().body(body).build()
                }

                client.fetchRawOutput(url, 3).test()
                    .assertValue { it.bytes.decodeToString() == "log" && it.limitReached }
                    .assertComplete().assertNoErrors()
                body.bytesRead shouldBe 6L
                body.closed shouldBe true
            }

            it("returns normal completed and live logs without a limit status") {
                val completed = TrackedBody("""[{"message":"one"},{"message":"two"}]""", declaredLength)
                val live = TrackedBody("live", declaredLength)
                val client = outputClient(64) { request ->
                    val body = if (request.header("Range") == null) completed else live
                    response(request, 200).newBuilder().body(body).build()
                }

                client.fetchOutput(url).test().assertResult("onetwo")
                client.fetchRawOutput(url, 0).test()
                    .assertValue { it.bytes.decodeToString() == "live" && !it.limitReached }
                    .assertComplete().assertNoErrors()
            }
        }
    }

    describe("fetchOutput()") {
        it("returns the whole completed log when its declared length matches the byte limit") {
            val body = """[{"message":"complete"}]"""
            val client = outputClient(body.toByteArray().size) { response(it, 200, body) }

            client.fetchOutput(url).test().assertResult("complete")
        }

        it("reports an incomplete JSON response that ended below the byte limit") {
            val client = outputClient { response(it, 200, """[{"message":"complete"}""") }

            client.fetchOutput(url).test().assertFailure(IOException::class.java)
        }

        it("should report the HTTP status before parsing an error body") {
            val client = outputClient { response(it, 503, "Service Unavailable") }

            client.fetchOutput(url).test()
                .assertError { it is IOException && it.message == "Action output request failed with HTTP 503" }
                .assertNoValues()
        }
    }

    describe("fetchFinalRawOutput()") {
        listOf("HTTP 503", "I/O error").forEach { failure ->
            it("should emit the remaining bytes once at the same offset after an initial $failure") {
                val scheduler = TestScheduler()
                val ranges = mutableListOf<String?>()
                val client = outputClient { request ->
                    ranges.add(request.header("Range"))
                    when {
                        ranges.size > 1 -> response(request, 206, "last lines", "bytes 42-51/52")
                        failure == "HTTP 503" -> response(request, 503, "Unavailable")
                        else -> throw IOException("Connection lost")
                    }
                }
                val observer = client.fetchFinalRawOutput(url, offset, scheduler).map { String(it.bytes) }.test()

                observer.assertEmpty()
                scheduler.advanceTimeBy(5, SECONDS)

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

            scheduler.advanceTimeBy(5, SECONDS)

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

            client.fetchFinalRawOutput(url, offset, TestScheduler()).map { it.bytes.size }.test().assertResult(0)
        }

        it("should accept a range response confirming that the entire log was already read") {
            val client = outputClient {
                response(it, 416).newBuilder().header("Content-Range", "bytes */42").build()
            }

            client.fetchFinalRawOutput(url, offset, TestScheduler()).map { it.bytes.size }.test().assertResult(0)
        }
    }

    describe("fetchRawOutput()") {
        it("returns no new bytes when a full response ends before the requested offset") {
            val client = outputClient { response(it, 200, "short") }

            client.fetchRawOutput(url, offset).test()
                .assertValue { it.bytes.isEmpty() && !it.limitReached }
                .assertComplete().assertNoErrors()
        }

        it("should append only new bytes when the server returns full logs") {
            val scheduler = TestScheduler()
            val ranges = mutableListOf<String?>()
            val client = outputClient { request ->
                ranges.add(request.header("Range"))
                val body = if (ranges.size == 1) "abc" else "abcdef"
                response(request, 200, body)
            }
            val observer = pollActionOutputs(listOf(url), Observable.never(), client::fetchRawOutput, scheduler).test()

            try {
                scheduler.advanceTimeBy(2, SECONDS)

                observer.assertValues("abc", "def").assertNoErrors()
                ranges shouldBe listOf("bytes=0-", "bytes=3-", "bytes=6-")
            } finally {
                observer.dispose()
            }
        }

        it("should interpret the full response offset as bytes") {
            val client = outputClient { response(it, 200, "猫log") }

            client.fetchRawOutput(url, 3L).map { it.bytes.decodeToString() }.test().assertResult("log")
        }

        listOf(null, "invalid", "bytes 41-43/44", "bytes 43-45/46").forEach { range ->
            it("should reject a partial response with Content-Range ${range ?: "missing"}") {
                val client = outputClient { response(it, 206, "log", range) }

                client.fetchRawOutput(url, offset).test().assertFailure(IOException::class.java)
            }
        }

        it("should run every fetch on IO and keep the polling timer free during a request") {
            verifyRawOutputPolling()
        }
    }
})

private fun verifyRawOutputPolling() {
    val timer = TestScheduler()
    val ioExecutor = Executors.newCachedThreadPool { Thread(it, "raw-output-io") }
    val completedTasks = LinkedBlockingQueue<Unit>()
    val ioScheduler = Schedulers.from { task ->
        ioExecutor.execute {
            try {
                task.run()
            } finally {
                completedTasks.add(Unit)
            }
        }
    }
    val repeatedRequestStarted = CountDownLatch(1)
    val releaseRepeatedRequest = CountDownLatch(1)
    val requestCount = AtomicInteger()
    val requests = LinkedBlockingQueue<Pair<String, String?>>()
    val httpClient = OkHttpClient.Builder().addInterceptor { chain ->
        val request = chain.request()
        requests.add(Thread.currentThread().name to request.header("Range"))
        val requestNumber = requestCount.incrementAndGet()
        if (requestNumber == 2) {
            repeatedRequestStarted.countDown()
            check(releaseRepeatedRequest.await(10, SECONDS))
        }
        val start = (requestNumber - 1) * 3
        response(request, 206, "log", "bytes $start-${start + 2}/*")
    }.build()
    val client = BuildOutputClient("token", httpClient, ioScheduler)
    val finished = PublishSubject.create<Unit>()
    var offset = 0L
    val url = "https://example.com/raw-output"
    val fetch = { client.fetchRawOutput(url, offset) }
    val observer = polling(5, SECONDS, timer, fetch)
        .takeUntil(finished)
        .concatWith(Observable.defer { client.fetchFinalRawOutput(url, offset, timer) })
        .doOnNext { offset += it.bytes.size }
        .test()

    try {
        completedTasks.poll(5, SECONDS) shouldBe Unit
        timer.advanceTimeBy(4, SECONDS)
        requestCount.get() shouldBe 1
        timer.advanceTimeBy(1, SECONDS)
        repeatedRequestStarted.await(5, SECONDS) shouldBe true

        var timerRan = false
        timer.scheduleDirect({ timerRan = true }, 5, SECONDS)
        timer.advanceTimeBy(10, SECONDS)
        timerRan shouldBe true
        requestCount.get() shouldBe 2
        observer.assertValueCount(1)

        releaseRepeatedRequest.countDown()
        completedTasks.poll(5, SECONDS) shouldBe Unit
        timer.advanceTimeBy(4, SECONDS)
        requestCount.get() shouldBe 2
        timer.scheduleDirect { finished.onNext(Unit) }
        timer.triggerActions()
        completedTasks.poll(5, SECONDS) shouldBe Unit

        observer.assertComplete().assertNoErrors().assertValueCount(3)
        requests.toList() shouldBe listOf(
            "raw-output-io" to "bytes=0-",
            "raw-output-io" to "bytes=3-",
            "raw-output-io" to "bytes=6-"
        )
    } finally {
        releaseRepeatedRequest.countDown()
        observer.dispose()
        ioExecutor.shutdownNow()
    }
}

private fun outputClient(limit: Int = 1024 * 1024, respond: (Request) -> Response): BuildOutputClient =
    BuildOutputClient(
        "token",
        OkHttpClient.Builder().addInterceptor { respond(it.request()) }.build(),
        Schedulers.trampoline(),
        limit
    )

private fun response(request: Request, status: Int, body: String = "", contentRange: String? = null): Response =
    Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(status)
        .message("Test response")
        .body(body.toResponseBody())
        .apply { contentRange?.let { header("Content-Range", it) } }
        .build()


private class TrackedBody(text: String, declaredLength: Boolean) : ResponseBody() {
    private val buffer = Buffer().writeUtf8(text)
    private val length = if (declaredLength) buffer.size else -1L
    var bytesRead = 0L
        private set
    var closed = false
        private set

    private val trackedSource = object : Source {
        override fun read(sink: Buffer, byteCount: Long): Long =
            buffer.read(sink, minOf(byteCount, 1)).also { if (it > 0) bytesRead += it }

        override fun timeout(): Timeout = Timeout.NONE

        override fun close() {
            closed = true
            buffer.close()
        }
    }.buffer()

    override fun contentType(): MediaType? = null
    override fun contentLength(): Long = length
    override fun source() = trackedSource
}
