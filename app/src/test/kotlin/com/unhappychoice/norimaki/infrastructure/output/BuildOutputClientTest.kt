package com.unhappychoice.norimaki.infrastructure.output

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

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
})
