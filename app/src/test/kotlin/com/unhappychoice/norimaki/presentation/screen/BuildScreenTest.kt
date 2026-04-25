package com.unhappychoice.norimaki.presentation.screen

import com.github.unhappychoice.circleci.v1.response.Build
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.domain.model.revisionString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk

class BuildScreenTest : DescribeSpec({
    lateinit var subject: BuildScreen
    lateinit var build: Build

    beforeEach {
        build = mockk {
            every { branch } returns "main"
            every { buildNum } returns 123
            every { vcsRevision } returns "abc123def"
        }
        subject = BuildScreen(build)
    }

    describe("BuildScreen") {
        describe(".getTitle()") {
            it("should return title") {
                subject.getTitle() shouldBe build.revisionString()
            }
        }
        describe(".getLayoutResource()") {
            it("should return view resource") {
                subject.getLayoutResource() shouldBe R.layout.build_view
            }
        }
    }
})
