package com.unhappychoice.norimaki.infrastructure.testresults

import com.google.gson.JsonSyntaxException
import com.unhappychoice.norimaki.domain.model.BuildId
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException
import java.util.concurrent.TimeUnit.SECONDS

class TestResultsClientTest : DescribeSpec({
    describe("fetch") {
        it("requests all pages with the session token and keeps failures and errors in order") {
            val requests = mutableListOf<Request>()
            val client = client { request ->
                requests.add(request)
                when (request.url.queryParameter("page-token")) {
                    null -> 200 to """{"items":[{"result":"success"}],"next_page_token":"a+/="}"""
                    "a+/=" -> 200 to """{"items":[{"result":"failure","name":"asserts","classname":"Example",
                        "message":"Expected <a>\nActual <b>"}],"next_page_token":"last"}"""
                    else -> 200 to """{"items":[{"result":"error","name":"throws","message":null}],
                        "next_page_token":null}"""
                }
            }

            client.fetch(buildId).test().awaitDone(5, SECONDS).assertResult(
                TestResults.FailurePage(listOf(TestFailure("asserts", "Example", "Expected <a>\nActual <b>"))),
                TestResults.FailurePage(listOf(TestFailure("throws", null, null))),
                TestResults.Reported
            )
            requests.map { it.url.encodedPath }.distinct() shouldBe listOf("/api/v2/project/gh/org/repo/123/tests")
            requests.map { it.header("Circle-Token") } shouldBe List(3) { "session-token" }
            requests.map { it.url.queryParameter("page-token") } shouldBe listOf(null, "a+/=", "last")
        }

        it("distinguishes reported results with no failures from unavailable metadata") {
            val passing = client {
                it.url.encodedPath shouldBe "/api/v2/project/bb/org/repo/123/tests"
                200 to """{"items":[{"result":"success"},{"result":"skipped"}]}"""
            }
            passing.fetch(buildId.copy(vcsType = "bitbucket")).test().awaitDone(5, SECONDS)
                .assertResult(TestResults.Reported)

            listOf(200 to """{"items":[]}""", 404 to "{}").forEach { response ->
                client { response }.fetch(buildId).test().awaitDone(5, SECONDS)
                    .assertResult(TestResults.Unavailable)
            }
        }

        it("reports unavailable metadata when the owner or repository is blank") {
            val client = client { error("Unexpected request") }
            listOf(buildId.copy(owner = " "), buildId.copy(repository = " ")).forEach { incomplete ->
                client.fetch(incomplete).test().awaitDone(5, SECONDS).assertResult(TestResults.Unavailable)
            }
        }

        it("stops pagination at an empty page token") {
            client { request ->
                request.url.query shouldBe null
                200 to """{"items":[{"result":"success"}],"next_page_token":""}"""
            }.fetch(buildId).test().awaitDone(5, SECONDS).assertResult(TestResults.Reported)
        }

        it("propagates request errors, including an unavailable subsequent page") {
            listOf(401, 403, 429, 500).forEach { status ->
                client { status to "{}" }.fetch(buildId).test().awaitDone(5, SECONDS)
                    .assertFailure(IOException::class.java)
            }
            client { request ->
                if (request.url.query == null) 200 to """{"items":[],"next_page_token":"next"}"""
                else 404 to "{}"
            }.fetch(buildId).test().awaitDone(5, SECONDS).assertFailure(IOException::class.java)
        }

        it("rejects a repeated pagination token") {
            client { 200 to """{"items":[],"next_page_token":"next"}""" }
                .fetch(buildId).test().awaitDone(5, SECONDS).assertFailure(IOException::class.java)
        }

        it("keeps emitted failures when a later page contains invalid JSON") {
            client { request ->
                val body = if (request.url.queryParameter("page-token") == "next") {
                    """{"items":["""
                } else {
                    """{"items":[{"result":"failure","name":"asserts"}],"next_page_token":"next"}"""
                }
                200 to body
            }.fetch(buildId).test().awaitDone(5, SECONDS).assertFailure(
                JsonSyntaxException::class.java,
                TestResults.FailurePage(listOf(TestFailure("asserts", null, null)))
            )
        }
    }
})

private val buildId = BuildId("github", "org", "repo", 123)

private fun client(respond: (Request) -> Pair<Int, String>): TestResultsClient =
    TestResultsClient("session-token", OkHttpClient.Builder().addInterceptor { chain ->
        val request = chain.request()
        val (status, body) = respond(request)
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
            .code(status).message("Test response").body(body.toResponseBody()).build()
    }.build())
