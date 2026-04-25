package com.unhappychoice.norimaki.extension

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class StringAnsiEscapeTest : DescribeSpec({
    describe("String+AnsiEscape") {
        it(".removeAnsiEscapeCode() should remove the code") {
            "[3ASome string".removeAnsiEscapeCode() shouldBe "Some string"
        }
        it(".replaceAnsiColorCodeToHtml() should replace 30 to #111111") {
            "[30mSome string".replaceAnsiColorCodeToHtml() shouldBe "<font color=#111111>Some string</font>"
        }
        it(".replaceAnsiColorCodeToHtml() should replace 31 to #F00000") {
            "[31mSome string".replaceAnsiColorCodeToHtml() shouldBe "<font color=#F00000>Some string</font>"
        }
        it(".replaceAnsiColorCodeToHtml() should replace 32 to #00F000") {
            "[32mSome string".replaceAnsiColorCodeToHtml() shouldBe "<font color=#00F000>Some string</font>"
        }
        it(".replaceAnsiColorCodeToHtml() should replace 33 to #F0F000") {
            "[33mSome string".replaceAnsiColorCodeToHtml() shouldBe "<font color=#F0F000>Some string</font>"
        }
        it(".replaceAnsiColorCodeToHtml() should replace 34 to #0000F0") {
            "[34mSome string".replaceAnsiColorCodeToHtml() shouldBe "<font color=#0000F0>Some string</font>"
        }
        it(".replaceAnsiColorCodeToHtml() should replace 35 to #F000F0") {
            "[35mSome string".replaceAnsiColorCodeToHtml() shouldBe "<font color=#F000F0>Some string</font>"
        }
        it(".replaceAnsiColorCodeToHtml() should replace 36 to #00F0F0") {
            "[36mSome string".replaceAnsiColorCodeToHtml() shouldBe "<font color=#00F0F0>Some string</font>"
        }
        it(".replaceAnsiColorCodeToHtml() should replace 37 to #F0F0F0") {
            "[37mSome string".replaceAnsiColorCodeToHtml() shouldBe "<font color=#F0F0F0>Some string</font>"
        }
        it(".replaceAnsiColorCodeToHtml() should replace 39 to #F0F0F0") {
            "[39mSome string".replaceAnsiColorCodeToHtml() shouldBe "<font color=#F0F0F0>Some string</font>"
        }
    }
})
