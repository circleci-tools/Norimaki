package com.unhappychoice.norimaki.presentation.screen

import com.github.unhappychoice.circleci.v1.response.Build
import com.github.unhappychoice.circleci.v1.response.BuildStep
import com.unhappychoice.norimaki.R
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk

class BuildStepScreenTest : DescribeSpec({
    lateinit var subject: BuildStepScreen
    lateinit var build: Build
    lateinit var buildStep: BuildStep

    beforeEach {
        build = mockk(relaxed = true)
        buildStep = mockk { every { name } returns "build name" }
        subject = BuildStepScreen(build, buildStep)
    }

    describe("BuildStepScreen") {
        describe(".getTitle()") {
            it("should return title") {
                subject.getTitle() shouldBe buildStep.name
            }
        }
        describe(".getLayoutResource()") {
            it("should return view resource") {
                subject.getLayoutResource() shouldBe R.layout.build_step_view
            }
        }
    }
})
