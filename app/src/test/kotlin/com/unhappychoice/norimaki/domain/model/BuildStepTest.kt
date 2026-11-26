package com.unhappychoice.norimaki.domain.model

import com.github.unhappychoice.circleci.v1.response.BuildAction
import com.github.unhappychoice.circleci.v1.response.BuildStep
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.util.Date

class BuildStepTest : DescribeSpec({
    fun action(
        millis: Int? = null,
        stepNumber: Int? = null,
        end: Date? = Date(),
        isCanceled: Boolean? = null,
        isFailed: Boolean? = null,
        isTimedout: Boolean? = null
    ): BuildAction = mockk {
        every { runTimeMillis } returns millis
        every { step } returns stepNumber
        every { endTime } returns end
        every { canceled } returns isCanceled
        every { failed } returns isFailed
        every { timedout } returns isTimedout
    }

    describe("BuildStep.runTime()") {
        it("should sum run time of actions as mm:ss") {
            BuildStep("test", listOf(action(millis = 61_000), action(millis = 4_500), action())).runTime() shouldBe "01:05"
        }
        it("should be zero without actions") {
            BuildStep("test", listOf()).runTime() shouldBe "00:00"
        }
    }

    describe("BuildStep.step()") {
        it("should return the step number of the first action") {
            BuildStep("test", listOf(action(stepNumber = 3), action(stepNumber = 4))).step() shouldBe 3
        }
        it("should return null without actions") {
            BuildStep("test", listOf()).step() shouldBe null
        }
    }

    describe("BuildStep.buildStatus()") {
        it("should be CANCELED when an action is canceled") {
            BuildStep("test", listOf(action(isCanceled = true, isFailed = true))).buildStatus() shouldBe BuildStatus.CANCELED
        }
        it("should be FAILED when an action failed or timed out") {
            BuildStep("test", listOf(action(isFailed = true))).buildStatus() shouldBe BuildStatus.FAILED
            BuildStep("test", listOf(action(isTimedout = true))).buildStatus() shouldBe BuildStatus.FAILED
        }
        it("should be SUCCESS when every action finished") {
            BuildStep("test", listOf(action(), action())).buildStatus() shouldBe BuildStatus.SUCCESS
        }
        it("should be ACTIVE while an action is running") {
            BuildStep("test", listOf(action(), action(end = null))).buildStatus() shouldBe BuildStatus.ACTIVE
        }
    }
})
