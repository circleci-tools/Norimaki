package com.unhappychoice.norimaki.presentation.screen

import com.unhappychoice.norimaki.R
import com.winterbe.expekt.expect
import io.polymorphicpanda.kspec.KSpec
import io.polymorphicpanda.kspec.describe
import io.polymorphicpanda.kspec.it
import io.polymorphicpanda.kspec.junit.JUnitKSpecRunner
import org.junit.runner.RunWith

@RunWith(JUnitKSpecRunner::class)
class APITokenScreenTest : KSpec() {
    lateinit var subject: APITokenScreen

    override fun spec() {
        beforeEach {
            subject = APITokenScreen()
        }

        describe("APITokenScreen") {
            describe(".getTitle()") {
                it("should return title") {
                    expect(subject.getTitle()).to.equal("Set api token")
                }
            }
            describe(".getLayoutResource()") {
                it("should return view resource") {
                    expect(subject.getLayoutResource()).to.equal(R.layout.api_token_view)
                }
            }
        }
    }
}
