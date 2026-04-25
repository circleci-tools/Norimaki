package com.unhappychoice.norimaki.presentation.screen

import com.unhappychoice.norimaki.R
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class BuildListScreenTest : DescribeSpec({
    val subject = BuildListScreen("")

    describe("BuildListScreen") {
        describe(".getTitle()") {
            it("should return 'Recent Builds' for empty path") {
                BuildListScreen("").getTitle() shouldBe "Recent Builds"
            }
            it("should return the path itself when set") {
                BuildListScreen("unhappychoice/Norimaki").getTitle() shouldBe "unhappychoice/Norimaki"
            }
        }
        describe(".getLayoutResource()") {
            it("should return view resource") {
                subject.getLayoutResource() shouldBe R.layout.build_list_view
            }
        }
    }
})
