package com.unhappychoice.norimaki.infrastructure.output

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException

class BuildOutputClientTest : DescribeSpec({
    describe("fetchOutput()") {
        it("should report the HTTP status before parsing an error body") {
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(503)
                    .message("Service Unavailable")
                    .body("Service Unavailable".toResponseBody())
                    .build()
            }.build()

            BuildOutputClient("token", client).fetchOutput("https://example.com/output").test()
                .assertError { it is IOException && it.message == "Action output request failed with HTTP 503" }
                .assertNoValues()
        }
    }

    describe("newRawOutput()") {
        val body = "abcdef".toByteArray()

        it("should return the body of a successful response") {
            String(newRawOutput(true, body)) shouldBe "abcdef"
        }
        it("should return nothing for a failed response") {
            newRawOutput(false, body).size shouldBe 0
        }
    }
})
