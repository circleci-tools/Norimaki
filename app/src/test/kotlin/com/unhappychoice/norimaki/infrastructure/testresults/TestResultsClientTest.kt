package com.unhappychoice.norimaki.infrastructure.testresults

import com.github.unhappychoice.circleci.v1.response.Build
import com.google.gson.JsonSyntaxException
import com.sun.net.httpserver.HttpServer
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
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.ConcurrentLinkedQueue
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
            requests.map { it.url.scheme to it.url.host }.distinct() shouldBe listOf("https" to "circleci.com")
            requests.map { it.url.port }.distinct() shouldBe listOf(443)
            requests.map { it.header("Circle-Token") } shouldBe List(3) { "session-token" }
            requests.map { it.url.queryParameter("page-token") } shouldBe listOf(null, "a+/=", "last")
        }

        it("rejects an off-origin redirect on the first page") {
            verifyRedirectRejected(redirectOnFirstPage = true)
        }

        it("rejects an off-origin redirect on a subsequent page") {
            verifyRedirectRejected(redirectOnFirstPage = false)
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

        it("reports unavailable metadata when the build identity is incomplete") {
            val client = client { error("Unexpected request") }
            listOf(build(owner = null), build(owner = " "), build(repository = null),
                build(repository = " "), build(job = null)).forEach { incomplete ->
                client.fetch(incomplete).test().awaitDone(5, SECONDS).assertResult(TestResults.Unavailable)
            }
        }

        it("stops pagination at an empty page token") {
            client { request ->
                request.url.query shouldBe null
                200 to """{"items":[{"result":"success"}],"next_page_token":""}"""
            }.fetch(build()).test().awaitDone(5, SECONDS).assertResult(TestResults.Reported(emptyList()))
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

        it("fails without emitting partial results when a later page contains invalid JSON") {
            client { request ->
                val body = if (request.url.queryParameter("page-token") == "next") {
                    """{"items":["""
                } else {
                    """{"items":[{"result":"failure","name":"asserts"}],"next_page_token":"next"}"""
                }
                200 to body
            }.fetch(build()).test().awaitDone(5, SECONDS).assertFailure(JsonSyntaxException::class.java)
        }
    }
})

private fun verifyRedirectRejected(redirectOnFirstPage: Boolean) {
    val requests = ConcurrentLinkedQueue<String>()
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    val port = server.address.port
    server.createContext("/") { exchange ->
        requests.add(exchange.requestURI.toString())
        when {
            exchange.requestURI.path == "/redirected" -> exchange.sendResponseHeaders(500, -1)
            redirectOnFirstPage || exchange.requestURI.query != null -> {
                exchange.responseHeaders.set("Location", "http://localhost:$port/redirected")
                exchange.sendResponseHeaders(302, -1)
            }
            else -> {
                val body = """{"items":[{"result":"success"}],"next_page_token":"next"}""".toByteArray()
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
        }
        exchange.close()
    }
    val httpClient = OkHttpClient.Builder().proxy(Proxy.NO_PROXY).addInterceptor { chain ->
        val request = chain.request()
        val localUrl = request.url.newBuilder().scheme("http").host("127.0.0.1").port(port).build()
        chain.proceed(request.newBuilder().url(localUrl).build())
    }.build()
    server.start()
    try {
        TestResultsClient("session-token", httpClient).fetch(build()).test().awaitDone(5, SECONDS)
            .assertFailure(IOException::class.java)
            .assertError { it.message == "Test results request failed with HTTP 302" }
        val path = "/api/v2/project/gh/org/repo/123/tests"
        requests.toList() shouldBe if (redirectOnFirstPage) listOf(path) else listOf(path, "$path?page-token=next")
    } finally {
        server.stop(0)
        httpClient.connectionPool.evictAll()
    }
}

private fun build(
    url: String = "https://github.com/org/repo",
    job: Int? = 123,
    owner: String? = "org",
    repository: String? = "repo"
): Build = mockk {
    every { vcsUrl } returns url
    every { username } returns owner
    every { reponame } returns repository
    every { buildNum } returns job
}

private fun client(respond: (Request) -> Pair<Int, String>): TestResultsClient =
    TestResultsClient("session-token", OkHttpClient.Builder().addInterceptor { chain ->
        val request = chain.request()
        val (status, body) = respond(request)
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
            .code(status).message("Test response").body(body.toResponseBody()).build()
    }.build())
