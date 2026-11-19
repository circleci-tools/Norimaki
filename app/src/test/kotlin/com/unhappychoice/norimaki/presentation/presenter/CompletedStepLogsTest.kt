package com.unhappychoice.norimaki.presentation.presenter

import com.unhappychoice.norimaki.extension.removeAnsiEscapeCode
import com.unhappychoice.norimaki.extension.replaceAnsiColorCodeToHtml
import com.unhappychoice.norimaki.infrastructure.output.BuildOutputClient
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CompletedStepLogsTest {
    @Test
    fun displaysSuccessfulDownloadsAroundAnHttpFailure() {
        val urls = listOf("first", "failed", "last").map { "https://example.com/$it" }
        val logs = completedActionLogs(urls, outputClient()::fetchOutput)
            .toList()
            .blockingGet()
            .joinToString("") { it.removeAnsiEscapeCode().replaceAnsiColorCodeToHtml() }

        assertEquals("first output<br>[Some output could not be loaded.]<br>last output", logs)
    }

    private fun outputClient(): BuildOutputClient {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val action = request.url.pathSegments.single()
            val (status, body) = when (action) {
                "failed" -> 503 to "Service Unavailable"
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
        return BuildOutputClient("token", client)
    }
}
