package com.unhappychoice.norimaki.infrastructure.flakytests

import com.unhappychoice.norimaki.domain.model.ProjectId
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException
import java.util.concurrent.TimeUnit.SECONDS

class FlakyTestsClientTest : DescribeSpec({
    describe("fetch") {
        it("requests the project's flaky tests with the session token and aggregates occurrences by test identity") {
            val requests = mutableListOf<Request>()
            val client = client { request ->
                requests.add(request)
                200 to """{"flaky-tests":[
                    {"test-name":"testA","classname":"ExampleTest","times-flaked":2},
                    {"test-name":"testA","classname":"ExampleTest","times-flaked":3},
                    {"test-name":"testB","classname":null,"times-flaked":7}
                ]}"""
            }

            client.fetch(projectId).test().awaitDone(5, SECONDS).assertResult(
                listOf(
                    FlakyTestSummary("testB", null, 7),
                    FlakyTestSummary("testA", "ExampleTest", 5)
                )
            )
            requests.map { it.url.encodedPath } shouldBe listOf("/api/v2/insights/gh/org/repo/flaky-tests")
            requests.map { it.url.scheme to it.url.host } shouldBe listOf("https" to "circleci.com")
            requests.map { it.header("Circle-Token") } shouldBe listOf("session-token")
        }

        it("uses the bitbucket slug for bitbucket projects") {
            val client = client { request ->
                request.url.encodedPath shouldBe "/api/v2/insights/bb/org/repo/flaky-tests"
                200 to """{"flaky-tests":[]}"""
            }
            client.fetch(projectId.copy(vcsType = "bitbucket")).test().awaitDone(5, SECONDS).assertResult(emptyList())
        }

        it("reports an empty list when there are no flaky tests") {
            client { 200 to """{"flaky-tests":[]}""" }.fetch(projectId).test().awaitDone(5, SECONDS)
                .assertResult(emptyList())
        }

        it("propagates request errors") {
            listOf(401, 403, 429, 500).forEach { status ->
                client { status to "{}" }.fetch(projectId).test().awaitDone(5, SECONDS)
                    .assertFailure(IOException::class.java)
            }
        }
    }
})

private val projectId = ProjectId("github", "org", "repo")

private fun client(respond: (Request) -> Pair<Int, String>): FlakyTestsClient =
    FlakyTestsClient("session-token", OkHttpClient.Builder().addInterceptor { chain ->
        val request = chain.request()
        val (status, body) = respond(request)
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
            .code(status).message("Test response").body(body.toResponseBody()).build()
    }.build())
