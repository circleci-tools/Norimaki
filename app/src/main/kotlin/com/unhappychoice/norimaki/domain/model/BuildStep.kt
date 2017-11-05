package com.unhappychoice.norimaki.domain.model

import android.graphics.Color
import com.github.unhappychoice.circleci.response.BuildAction
import com.github.unhappychoice.circleci.response.BuildStep


fun BuildStep.runTime(): String {
    val milliSeconds = actions.fold(0) { acc, action -> acc + (action.runTimeMillis ?: 0) }
    val seconds = milliSeconds / 1000
    return String.format("%02d:%02d", seconds / 60, seconds % 60)
}

fun BuildStep.statusColor(): Int = when {
    isCanceled() -> Color.rgb(137, 137, 137)
    isFailed() -> Color.rgb(237, 92, 92)
    isFinished() -> Color.rgb(66, 200, 138)
    else -> Color.rgb(92, 211, 228)
}

fun BuildStep.addAction(action: BuildAction): BuildStep = copy(
    actions = (listOf(action) + actions).distinctBy { it.name }
)

fun BuildStep.step(): Int? = actions.firstOrNull()?.step

fun List<BuildStep>.addAction(action: BuildAction): List<BuildStep> = map {
    when (it.name) {
        action.name -> it.addAction(action)
        else -> it
    }
}

private fun BuildStep.isCanceled() =
    actions.fold(false) { acc, action -> acc || (action.canceled ?: false) }

private fun BuildStep.isFailed() =
    actions.fold(false) { acc, action -> acc || (action.failed ?: false) || (action.timedout ?: false) }

private fun BuildStep.isFinished() =
    actions.fold(true) { acc, action -> acc && action.endTime != null }