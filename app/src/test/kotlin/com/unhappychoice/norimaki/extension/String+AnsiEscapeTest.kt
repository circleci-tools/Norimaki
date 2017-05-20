package com.unhappychoice.norimaki.extension

import com.winterbe.expekt.expect
import io.polymorphicpanda.kspec.KSpec
import io.polymorphicpanda.kspec.describe
import io.polymorphicpanda.kspec.it
import io.polymorphicpanda.kspec.junit.JUnitKSpecRunner
import org.junit.runner.RunWith

@RunWith(JUnitKSpecRunner::class)
class StringAnsiEscapeTest : KSpec() {
    override fun spec() {
        describe("String+AnsiEscape") {
            it(".removeAnsiEscapeCode() should remove the code") {
                expect("\u001b[3ASome string".removeAnsiEscapeCode()).to.equal("Some string")
            }
            it(".replaceAnsiColorCodeToHtml() should replace the code to html tag") {
                val subject = "\u001b[30mSome string".replaceAnsiColorCodeToHtml()
                expect(subject).to.equal("<font color=#111111>Some string</font>")
            }
            it(".replaceAnsiColorCodeToHtml() should replace the code to html tag") {
                val subject = "\u001b[31mSome string".replaceAnsiColorCodeToHtml()
                expect(subject).to.equal("<font color=#F00000>Some string</font>")
            }
            it(".replaceAnsiColorCodeToHtml() should replace the code to html tag") {
                val subject = "\u001b[32mSome string".replaceAnsiColorCodeToHtml()
                expect(subject).to.equal("<font color=#00F000>Some string</font>")
            }
            it(".replaceAnsiColorCodeToHtml() should replace the code to html tag") {
                val subject = "\u001b[33mSome string".replaceAnsiColorCodeToHtml()
                expect(subject).to.equal("<font color=#F0F000>Some string</font>")
            }
            it(".replaceAnsiColorCodeToHtml() should replace the code to html tag") {
                val subject = "\u001b[34mSome string".replaceAnsiColorCodeToHtml()
                expect(subject).to.equal("<font color=#0000F0>Some string</font>")
            }
            it(".replaceAnsiColorCodeToHtml() should replace the code to html tag") {
                val subject = "\u001b[35mSome string".replaceAnsiColorCodeToHtml()
                expect(subject).to.equal("<font color=#F000F0>Some string</font>")
            }
            it(".replaceAnsiColorCodeToHtml() should replace the code to html tag") {
                val subject = "\u001b[36mSome string".replaceAnsiColorCodeToHtml()
                expect(subject).to.equal("<font color=#00F0F0>Some string</font>")
            }
            it(".replaceAnsiColorCodeToHtml() should replace the code to html tag") {
                val subject = "\u001b[37mSome string".replaceAnsiColorCodeToHtml()
                expect(subject).to.equal("<font color=#F0F0F0>Some string</font>")
            }
            it(".replaceAnsiColorCodeToHtml() should replace the code to html tag") {
                val subject = "\u001b[39mSome string".replaceAnsiColorCodeToHtml()
                expect(subject).to.equal("<font color=#F0F0F0>Some string</font>")
            }
        }
    }
}
