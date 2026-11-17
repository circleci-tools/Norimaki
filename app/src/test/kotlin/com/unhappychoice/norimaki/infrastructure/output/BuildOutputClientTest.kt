package com.unhappychoice.norimaki.infrastructure.output

import com.unhappychoice.norimaki.extension.polling
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers
import io.reactivex.schedulers.TestScheduler
import io.reactivex.subjects.PublishSubject
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicInteger

class BuildOutputClientTest : DescribeSpec({
    describe("newRawOutput()") {
        val body = "abcdef".toByteArray()

        it("should return the body of a successful response") {
            String(newRawOutput(true, body)) shouldBe "abcdef"
        }
        it("should return nothing for a failed response") {
            newRawOutput(false, body).size shouldBe 0
        }
    }

    describe("fetchRawOutput()") {
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
        if (requestCount.incrementAndGet() == 2) {
            repeatedRequestStarted.countDown()
            check(releaseRepeatedRequest.await(10, SECONDS))
        }
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(206)
            .message("Partial Content")
            .body("log".toResponseBody())
            .build()
    }.build()
    val client = BuildOutputClient("token", httpClient, ioScheduler)
    val finished = PublishSubject.create<Unit>()
    var offset = 0L
    val fetch = { client.fetchRawOutput("https://example.com/raw-output", offset) }
    val observer = polling(5, SECONDS, timer, fetch)
        .takeUntil(finished)
        .concatWith(Observable.defer(fetch))
        .doOnNext { offset += it.size }
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
