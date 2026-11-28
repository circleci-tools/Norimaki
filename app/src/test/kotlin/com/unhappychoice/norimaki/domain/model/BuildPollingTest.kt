package com.unhappychoice.norimaki.domain.model

import com.github.unhappychoice.circleci.v1.response.Build
import com.github.unhappychoice.circleci.v1.response.BuildAction
import com.github.unhappychoice.circleci.v1.response.BuildStep
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.util.Date

class BuildPollingTest : DescribeSpec({
    fun buildWithLifecycle(value: String?): Build = mockk { every { lifecycle } returns value }
    fun action(end: Date?): BuildAction = mockk { every { endTime } returns end }

    describe("Build.isFinished()") {
        it("should be true for finished and not_run builds") {
            buildWithLifecycle("finished").isFinished() shouldBe true
            buildWithLifecycle("not_run").isFinished() shouldBe true
        }
        it("should be false for running or queued builds") {
            buildWithLifecycle("running").isFinished() shouldBe false
            buildWithLifecycle("queued").isFinished() shouldBe false
        }
    }

    describe("Build.rawOutputUrl()") {
        it("should point to the raw output of the action") {
            val build = mockk<Build> {
                every { vcsUrl } returns "https://github.com/circleci-tools/Norimaki"
                every { username } returns "circleci-tools"
                every { reponame } returns "Norimaki"
                every { buildNum } returns 42
            }
            val buildAction = mockk<BuildAction> {
                every { index } returns 0
                every { step } returns 101
            }

            build.rawOutputUrl(buildAction) shouldBe
                "https://circleci.com/api/private/output/raw/github/circleci-tools/Norimaki/42/output/0/101"
        }
    }

    describe("BuildStep.isFinished()") {
        it("should be true when every action has ended") {
            BuildStep("test", listOf(action(Date()), action(Date()))).isFinished() shouldBe true
        }
        it("should be false while any action is running") {
            BuildStep("test", listOf(action(Date()), action(null))).isFinished() shouldBe false
        }
    }

    describe("List<BuildStep>.findByStepNumber()") {
        it("should match by step number even when step names are identical") {
            val first = BuildStep("Run tests", listOf(mockk { every { step } returns 2 }))
            val second = BuildStep("Run tests", listOf(mockk { every { step } returns 5 }))
            val steps = listOf(first, second)

            steps.findByStepNumber(5) shouldBe second
            steps.findByStepNumber(3) shouldBe null
        }

        it("should ignore missing step numbers") {
            val steps = listOf(BuildStep("Run tests", emptyList()))

            steps.findByStepNumber(null) shouldBe null
        }

        it("should use the position to disambiguate duplicate step numbers") {
            val first = BuildStep("Run tests", listOf(mockk { every { step } returns 1 }))
            val second = BuildStep("Run tests", listOf(mockk { every { step } returns 1 }))
            val steps = listOf(first, second)

            steps.findByStepNumber(1, position = 0) shouldBe first
            steps.findByStepNumber(1, position = 1) shouldBe second
        }

        it("should fall back to the first match when the position no longer identifies a duplicate") {
            val first = BuildStep("Run tests", listOf(mockk { every { step } returns 1 }))
            val second = BuildStep("Run tests", listOf(mockk { every { step } returns 1 }))
            val steps = listOf(first, second)

            steps.findByStepNumber(1, position = 5) shouldBe first
            steps.findByStepNumber(1, position = null) shouldBe first
        }
    }
})
