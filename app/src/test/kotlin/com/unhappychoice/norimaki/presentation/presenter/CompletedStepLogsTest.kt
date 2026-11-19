package com.unhappychoice.norimaki.presentation.presenter

import com.unhappychoice.norimaki.infrastructure.output.BuildOutputClient
import com.unhappychoice.norimaki.infrastructure.output.OUTPUT_LIMIT_MESSAGE
import io.kotest.core.spec.style.DescribeSpec
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

class CompletedStepLogsTest : DescribeSpec({
    it("displays accepted output and the limit status alongside other action logs") {
        val client = outputClient()
        val urls = listOf("first", "failed", "capped", "last").map { "https://example.com/$it" }

        completedActionLogs(urls, client::fetchOutput).test()
            .assertResult(
                "first output",
                "\n[Some output could not be loaded.]\n",
                "accepted" + OUTPUT_LIMIT_MESSAGE,
                "last output"
            )
    }
})

private fun outputClient(): BuildOutputClient {
    val client = OkHttpClient.Builder().addInterceptor { chain ->
        val request = chain.request()
        val action = request.url.pathSegments.single()
        val (status, body) = when (action) {
            "failed" -> 503 to "Service Unavailable"
            "capped" -> 200 to """[{"message":"accepted"},{"message":"${"x".repeat(100)}"}]"""
            else -> 200 to """[{"message":"$action "},{"message":"output"}]"""
        }
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(status)
            .message("Test response")
            .body(body.toResponseBody())
            .build()
    }.build()
    return BuildOutputClient("token", client, outputByteLimit = 64)
}
