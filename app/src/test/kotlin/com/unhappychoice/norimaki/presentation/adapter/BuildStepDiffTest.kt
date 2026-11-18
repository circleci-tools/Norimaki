package com.unhappychoice.norimaki.presentation.adapter

import androidx.recyclerview.widget.DiffUtil
import com.github.unhappychoice.circleci.v1.response.BuildAction
import com.github.unhappychoice.circleci.v1.response.BuildStep
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.util.Date

class BuildStepDiffTest : DescribeSpec({
    describe("BuildStepDiff") {
        it("preserves numbered row identity across runtime and completion updates") {
            val running = step(1, runTimeMillis = 1000)
            val later = step(1, runTimeMillis = 2000)
            val completed = step(1, runTimeMillis = 2000, endTime = Date(2000))
            val runtimeUpdate = DiffUtil.calculateDiff(BuildStepDiff(listOf(running), listOf(later)))
            val completionUpdate = DiffUtil.calculateDiff(BuildStepDiff(listOf(later), listOf(completed)))

            runtimeUpdate.convertOldPositionToNew(0) shouldBe 0
            completionUpdate.convertOldPositionToNew(0) shouldBe 0
            BuildStepDiff(listOf(running), listOf(later)).areContentsTheSame(0, 0) shouldBe false
            BuildStepDiff(listOf(later), listOf(completed)).areContentsTheSame(0, 0) shouldBe false
        }

        it("matches same-named numbered steps by number when they move") {
            val steps = listOf(step(1), step(2))
            val diff = DiffUtil.calculateDiff(BuildStepDiff(steps, steps.reversed()))

            diff.convertOldPositionToNew(0) shouldBe 1
            diff.convertOldPositionToNew(1) shouldBe 0
        }

        it("does not match rows with empty actions, missing numbers, or duplicate numbers") {
            val empty = BuildStep("test", emptyList())
            val oldSteps = listOf(empty, empty.copy(), step(null), step(1), step(1), step(2))
            val newSteps = listOf(empty.copy(), step(null), empty.copy(), step(1), step(2))
            val diff = DiffUtil.calculateDiff(BuildStepDiff(oldSteps, newSteps))

            oldSteps.indices.map(diff::convertOldPositionToNew) shouldBe listOf(-1, -1, -1, -1, -1, 4)
            newSteps.indices.map(diff::convertNewPositionToOld) shouldBe listOf(-1, -1, -1, -1, 5)
        }
    }
}) {
    companion object {
        private fun step(number: Int?, runTimeMillis: Int? = null, endTime: Date? = null): BuildStep =
            BuildStep("test", listOf(BuildAction(
                bashCommand = null,
                canceled = null,
                endTime = endTime,
                exitCode = null,
                failed = null,
                hasOutput = null,
                infrastructureFail = null,
                index = null,
                name = null,
                outputUrl = null,
                parallel = null,
                runTimeMillis = runTimeMillis,
                startTime = null,
                status = null,
                step = number,
                timedout = null,
                truncated = null,
                type = null
            )))
    }
}
