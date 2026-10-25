package com.unhappychoice.norimaki.domain.model

import android.graphics.Color
import com.github.unhappychoice.circleci.v1.response.BuildAction
import com.github.unhappychoice.circleci.v1.response.BuildStep
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
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

    describe("BuildStep.statusColor()") {
        beforeEach {
            mockkStatic(Color::class)
            every { Color.rgb(any<Int>(), any<Int>(), any<Int>()) } answers { (firstArg<Int>() shl 16) or (secondArg<Int>() shl 8) or thirdArg<Int>() }
        }
        afterEach { unmockkStatic(Color::class) }

        it("should be gray when an action is canceled") {
            BuildStep("test", listOf(action(isCanceled = true, isFailed = true))).statusColor() shouldBe 0x898989
        }
        it("should be red when an action failed or timed out") {
            BuildStep("test", listOf(action(isFailed = true))).statusColor() shouldBe 0xED5C5C
            BuildStep("test", listOf(action(isTimedout = true))).statusColor() shouldBe 0xED5C5C
        }
        it("should be green when every action finished") {
            BuildStep("test", listOf(action(), action())).statusColor() shouldBe 0x42C88A
        }
        it("should be blue while an action is running") {
            BuildStep("test", listOf(action(), action(end = null))).statusColor() shouldBe 0x5CD3E4
        }
    }
})
