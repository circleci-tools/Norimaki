package com.unhappychoice.norimaki.infrastructure.workflow

import com.unhappychoice.norimaki.domain.model.WorkflowJob
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.reactivex.observers.TestObserver
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class WorkflowClientTest : DescribeSpec({
    describe("workflow jobs") {
        it("loads all pages with the personal token and includes holds without build numbers") {
            val requests = mutableListOf<Request>()
            val client = workflowClient { request ->
                requests.add(request)
                if (requests.size == 1) {
                    response(request, 200, """
                        {"items":[{"id":"build","name":"test","job_number":42,"status":"on_hold","type":"build","approval_request_id":"not-an-approval"}],
                         "next_page_token":"a+/="}
                    """.trimIndent())
                } else {
                    response(request, 200, """
                        {"items":[{"id":"hold","name":"deploy","status":"on_hold","type":"approval",
                                   "approval_request_id":"request"}],"next_page_token":null}
                    """.trimIndent())
                }
            }

            val jobs = client.jobs("run").blockingGet()

            jobs.map { it.jobNumber } shouldBe listOf(42, null)
            jobs.map { it.pendingApprovalId } shouldBe listOf(null, "request")
            requests.map { it.method } shouldBe listOf("GET", "GET")
            requests.map { it.header("Circle-Token") } shouldBe listOf("personal-token", "personal-token")
            requests.map { it.url.encodedPath } shouldBe List(2) { "/api/v2/workflow/run/job" }
            requests.map { it.url.queryParameter("page-token") } shouldBe listOf(null, "a+/=")
        }

        it("cancels the active call and stops pagination when disposed") {
            val started = CountDownLatch(1)
            val releaseResponse = CountDownLatch(1)
            val requestCount = AtomicInteger()
            val firstCall = AtomicReference<Call>()
            val httpClient = OkHttpClient.Builder().addInterceptor { chain ->
                if (requestCount.incrementAndGet() == 1) {
                    firstCall.set(chain.call())
                    started.countDown()
                    check(releaseResponse.await(5, SECONDS))
                    response(chain.request(), 200, """{"items":[],"next_page_token":"next"}""")
                } else {
                    response(chain.request(), 200, """{"items":[]}""")
                }
            }.build()
            val observer = TestObserver<List<WorkflowJob>>()
            val executor = Executors.newSingleThreadExecutor()

            try {
                val load = executor.submit {
                    WorkflowClient("personal-token", httpClient).jobs("run").subscribe(observer)
                }
                started.await(5, SECONDS) shouldBe true
                observer.dispose()
                val canceled = firstCall.get().isCanceled()
                releaseResponse.countDown()
                load.get(5, SECONDS)

                canceled shouldBe true
                requestCount.get() shouldBe 1
                observer.assertEmpty()
            } finally {
                observer.dispose()
                releaseResponse.countDown()
                executor.shutdownNow()
            }
        }

        it("stops at a blank page token and ignores blank approval IDs") {
            val requests = mutableListOf<Request>()
            val client = workflowClient { request ->
                requests.add(request)
                response(request, 200, """
                    {"items":[{"id":"hold","name":"deploy","status":"on_hold","type":"approval",
                               "approval_request_id":" "}],"next_page_token":" "}
                """.trimIndent())
            }

            client.jobs("run").blockingGet().single().pendingApprovalId shouldBe null
            requests.size shouldBe 1
        }

        it("fails when CircleCI repeats a pagination token") {
            val client = workflowClient { response(it, 200, """{"items":[],"next_page_token":"same"}""") }

            client.jobs("run").test().assertFailure(IOException::class.java)
        }
    }

    describe("job approval") {
        it("posts to the approval request ID using the personal token") {
            val client = workflowClient { request ->
                request.method shouldBe "POST"
                request.url.encodedPath shouldBe "/api/v2/workflow/run/approve/request"
                request.header("Circle-Token") shouldBe "personal-token"
                response(request, 202)
            }

            client.approve("run", "request").test().assertComplete()
        }

        it("exposes permission errors to the caller") {
            val client = workflowClient { response(it, 403) }

            client.approve("run", "request").test().assertError {
                it is WorkflowHttpException && it.status == 403
            }
        }
    }
})

private fun workflowClient(respond: (Request) -> Response) = WorkflowClient(
    "personal-token",
    OkHttpClient.Builder().addInterceptor { respond(it.request()) }.build()
)

private fun response(request: Request, status: Int, body: String = "") = Response.Builder()
    .request(request)
    .protocol(Protocol.HTTP_1_1)
    .code(status)
    .message("Test response")
    .body(body.toResponseBody())
    .build()
