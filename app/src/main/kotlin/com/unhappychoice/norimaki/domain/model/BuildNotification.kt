package com.unhappychoice.norimaki.domain.model

import com.github.unhappychoice.circleci.v1.response.Build
import java.util.Date

fun Build.notificationTitle(): String = "${statusLabel()}: ${repositoryString()}"

fun Build.notificationText(): String = listOfNotNull(revisionString(), subject).joinToString("\n")

fun List<Build>.finishedAfter(time: Date): List<Build> = filter { it.stopTime?.after(time) == true }

fun List<Build>.latestStopTime(): Date? = mapNotNull { it.stopTime }.maxOrNull()

private fun Build.statusLabel(): String =
    (status ?: "finished").replace("_", " ").replaceFirstChar { it.uppercase() }
