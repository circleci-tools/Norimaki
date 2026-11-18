package com.unhappychoice.norimaki.infrastructure.workflow

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException

class WorkflowClientTest : DescribeSpec({
    describe("workflow jobs") {
        it("loads all pages with the personal token and includes holds without build numbers") {
            val requests = mutableListOf<Request>()
            val client = workflowClient { request ->
                requests.add(request)
                if (requests.size == 1) {
                    response(request, 200, """
                        {"items":[{"id":"build","name":"test","job_number":42,"status":"success","type":"build"}],
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
