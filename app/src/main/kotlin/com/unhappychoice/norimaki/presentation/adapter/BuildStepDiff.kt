package com.unhappychoice.norimaki.presentation.adapter

import androidx.recyclerview.widget.DiffUtil
import com.github.unhappychoice.circleci.v1.response.BuildStep
import com.unhappychoice.norimaki.domain.model.step

internal class BuildStepDiff(
    private val oldSteps: List<BuildStep>,
    private val newSteps: List<BuildStep>
) : DiffUtil.Callback() {
    override fun getOldListSize(): Int = oldSteps.size

    override fun getNewListSize(): Int = newSteps.size

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
        oldNumbers[oldItemPosition]?.let { it == newNumbers[newItemPosition] } ?: false

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
        oldSteps[oldItemPosition] == newSteps[newItemPosition]

    private val oldNumbers = uniqueNumbers(oldSteps)
    private val newNumbers = uniqueNumbers(newSteps)

    private fun uniqueNumbers(steps: List<BuildStep>): List<Int?> {
        val numbers = steps.map { it.step() }
        val counts = numbers.filterNotNull().groupingBy { it }.eachCount()
        return numbers.map { number -> number?.takeIf { counts[it] == 1 } }
    }
}
