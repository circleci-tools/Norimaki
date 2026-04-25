package com.unhappychoice.norimaki.presentation.screen

import com.unhappychoice.norimaki.R
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class APITokenScreenTest : DescribeSpec({
    val subject = APITokenScreen()

    describe("APITokenScreen") {
        describe(".getTitle()") {
            it("should return title") {
                subject.getTitle() shouldBe "Set api token"
            }
        }
        describe(".getLayoutResource()") {
            it("should return view resource") {
                subject.getLayoutResource() shouldBe R.layout.api_token_view
            }
        }
    }
})
