package com.unhappychoice.norimaki.extension

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class StringAnsiEscapeTest : DescribeSpec({
    describe("String+AnsiEscape") {
        it(".removeAnsiEscapeCode() should remove the code") {
            "[3ASome string".removeAnsiEscapeCode() shouldBe "Some string"
        }
        it("should strip split hyperlink controls and preserve split color codes") {
            val controls = listOf(
                "\u001B]" to "\u0007",
                "\u001B]" to "\u001B\\",
                "\u009D" to "\u009C"
            )
            controls.forEach { (osc, terminator) ->
                val log = "before ${osc}8;id=hidden;https://example.test/report${terminator}" +
                    "\u001B[3A\u001B[31mReport\u001B[0m${osc}8;;${terminator} after"
                (0..log.length).forEach { split ->
                    val stream = AnsiEscapeStream()
                    val visible = stream.strip(log.take(split)) + stream.strip(log.drop(split))
                    visible shouldBe "before \u001B[31mReport\u001B[0m after"
                }
            }
        }

        it("should strip terminal controls across every chunk boundary") {
            val controls = listOf(
                "\u001B]0;hidden title\u0007",
                "\u009D52;c;hidden clipboard\u001B\\",
                "\u001BP1\$qhidden device response\u001B\\",
                "\u0090hidden device response\u009C",
                "\u001BXhidden string\u001B\\",
                "\u0098hidden string\u009C",
                "\u001B^hidden message\u001B\\",
                "\u009Ehidden message\u009C",
                "\u001B_hidden command\u001B\\",
                "\u009Fhidden command\u009C",
                "\u001B]0;cancelled title\u0018",
                "\u009B2J",
                "\u001B(B",
                "\u001B7",
                "\u0085"
            )
            controls.forEach { control ->
                val log = "before\n${control}after"
                (0..log.length).forEach { split ->
                    val stream = AnsiEscapeStream()
                    (stream.strip(log.take(split)) + stream.strip(log.drop(split))) shouldBe "before\nafter"
                }
            }
        }

        it(".replaceAnsiColorCodeToHtml() should escape literal tags and entities") {
            "expected <b>literal</b> &amp;".replaceAnsiColorCodeToHtml() shouldBe
                "expected &lt;b&gt;literal&lt;/b&gt; &amp;amp;"
        }
        it(".replaceAnsiColorCodeToHtml() should preserve colors and line breaks around escaped text") {
            "plain<br>\n\u001B[31m<error> &lt;\nnext\u001B[0m &".replaceAnsiColorCodeToHtml() shouldBe
                "plain&lt;br&gt;<br><font color=#F00000>&lt;error&gt; &amp;lt;<br>next</font> &amp;"
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
