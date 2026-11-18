package com.unhappychoice.norimaki.infrastructure.testresults

import com.github.unhappychoice.circleci.v1.response.Build
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
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

            client.fetch(build()).test().awaitDone(5, SECONDS).assertResult(
                TestResults.Reported(
                    listOf(TestFailure("asserts", "Example", "Expected <a>\nActual <b>"),
                        TestFailure("throws", null, null))
                )
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
            passing.fetch(build("https://bitbucket.org/org/repo")).test().awaitDone(5, SECONDS)
                .assertResult(TestResults.Reported(emptyList()))

            listOf(200 to """{"items":[]}""", 404 to "{}").forEach { response ->
                client { response }.fetch(build()).test().awaitDone(5, SECONDS)
                    .assertResult(TestResults.Unavailable)
            }
        }

        it("reports unavailable metadata when the build is missing its job number") {
            client { error("Unexpected request") }.fetch(build(job = null)).test().awaitDone(5, SECONDS)
                .assertResult(TestResults.Unavailable)
        }

        it("propagates request errors, including an unavailable subsequent page") {
            listOf(401, 403, 429, 500).forEach { status ->
                client { status to "{}" }.fetch(build()).test().awaitDone(5, SECONDS)
                    .assertFailure(IOException::class.java)
            }
            client { request ->
                if (request.url.query == null) 200 to """{"items":[],"next_page_token":"next"}"""
                else 404 to "{}"
            }.fetch(build()).test().awaitDone(5, SECONDS).assertFailure(IOException::class.java)
        }

        it("rejects a repeated pagination token") {
            client { 200 to """{"items":[],"next_page_token":"next"}""" }
                .fetch(build()).test().awaitDone(5, SECONDS).assertFailure(IOException::class.java)
        }
    }
})

private fun build(url: String = "https://github.com/org/repo", job: Int? = 123): Build = mockk {
    every { vcsUrl } returns url
    every { username } returns "org"
    every { reponame } returns "repo"
    every { buildNum } returns job
}

private fun client(respond: (Request) -> Pair<Int, String>): TestResultsClient =
    TestResultsClient("session-token", OkHttpClient.Builder().addInterceptor { chain ->
        val request = chain.request()
        val (status, body) = respond(request)
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
            .code(status).message("Test response").body(body.toResponseBody()).build()
    }.build())
