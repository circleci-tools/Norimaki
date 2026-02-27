package com.unhappychoice.norimaki.domain.model

import android.graphics.Color
import com.github.unhappychoice.circleci.v2.response.Workflow

fun Workflow.projectName(): String {
    val parts = projectSlug.split("/")
    return if (parts.size >= 3) "${parts[1]}/${parts[2]}" else projectSlug
}

fun Workflow.statusColor(): Int = when (status) {
    "success", "fixed" -> Color.rgb(66, 200, 138)
    "canceled" -> Color.rgb(137, 137, 137)
    "infrastructure_fail", "timedout", "failed", "error" -> Color.rgb(237, 92, 92)
    else -> Color.rgb(92, 211, 228)
}

fun List<Workflow>.addDistinctById(workflows: List<Workflow>) =
    (workflows + this).distinctBy { it.id }

fun List<Workflow>.sortByCreatedAt() = this.sortedByDescending { it.createdAt }
