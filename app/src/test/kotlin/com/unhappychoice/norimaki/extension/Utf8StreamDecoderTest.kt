package com.unhappychoice.norimaki.extension

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class Utf8StreamDecoderTest : DescribeSpec({
    describe("Utf8StreamDecoder") {
        it("decodes a character split between responses") {
            val decoder = Utf8StreamDecoder()

            decoder.decode(byteArrayOf(0x41, 0xC3.toByte())) shouldBe "A"
            decoder.decode(byteArrayOf()) shouldBe ""
            decoder.decode(byteArrayOf(0xA9.toByte(), 0x42)) shouldBe "éB"
            decoder.finish() shouldBe ""
        }

        it("replaces an incomplete sequence when the final response ends") {
            val decoder = Utf8StreamDecoder()

            decoder.decode(byteArrayOf(0xE2.toByte(), 0x82.toByte())) shouldBe ""
            decoder.finish() shouldBe "\uFFFD"
        }
    }
})
