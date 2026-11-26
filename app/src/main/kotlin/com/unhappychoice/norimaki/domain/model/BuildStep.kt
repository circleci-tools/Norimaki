package com.unhappychoice.norimaki.domain.model

import com.github.unhappychoice.circleci.v1.response.BuildStep


fun BuildStep.runTime(): String {
    val milliSeconds = actions.fold(0) { acc, action -> acc + (action.runTimeMillis ?: 0) }
    val seconds = milliSeconds / 1000
    return String.format("%02d:%02d", seconds / 60, seconds % 60)
}

fun BuildStep.buildStatus(): BuildStatus = when {
    isCanceled() -> BuildStatus.CANCELED
    isFailed() -> BuildStatus.FAILED
    isFinished() -> BuildStatus.SUCCESS
    else -> BuildStatus.ACTIVE
}

fun BuildStep.step(): Int? = actions.firstOrNull()?.step

fun BuildStep.isFinished() =
    actions.fold(true) { acc, action -> acc && action.endTime != null }

fun List<BuildStep>.findByStepNumber(stepNumber: Int?): BuildStep? =
    stepNumber?.let { number -> firstOrNull { it.step() == number } }

private fun BuildStep.isCanceled() =
    actions.fold(false) { acc, action -> acc || (action.canceled ?: false) }

private fun BuildStep.isFailed() =
    actions.fold(false) { acc, action -> acc || (action.failed ?: false) || (action.timedout ?: false) }