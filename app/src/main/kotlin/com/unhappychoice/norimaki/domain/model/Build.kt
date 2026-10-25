package com.unhappychoice.norimaki.domain.model

import android.graphics.Color
import com.github.unhappychoice.circleci.v1.response.Build
import com.github.unhappychoice.circleci.v1.response.BuildAction

fun Build.repositoryString(): String = "$username / $reponame"
fun Build.revisionString(): String = "$branch #$buildNum (${vcsRevision?.take(6)})"

fun Build.statusColor(): Int = when (status) {
    "success", "fixed", "no_tests" -> Color.rgb(66, 200, 138)
    "canceled" -> Color.rgb(137, 137, 137)
    "infrastructure_fail", "timedout", "failed" -> Color.rgb(237, 92, 92)
    else -> Color.rgb(92, 211, 228)
}

fun Build.avatarUrl(): String = "https://github.com/${user?.get("login")}.png"

fun Build.uniqueId(): String = "${repositoryString()}/$buildNum"

fun Build.isFinished(): Boolean = lifecycle in listOf("finished", "not_run")

fun Build.rawOutputUrl(action: BuildAction): String =
    "https://circleci.com/api/private/output/raw/${vcsType()}/$username/$reponame/$buildNum/output/${action.index}/${action.step}"

fun Build.vcsType(): String = vcsTypeFromUrl(vcsUrl)

fun vcsTypeFromUrl(url: String?): String = when {
    url?.contains("bitbucket.org") == true -> "bitbucket"
    else -> "github"
}

fun List<Build>.addDistinctByNumber(builds: List<Build>) = (builds + this).distinctBy { it.uniqueId() }
fun List<Build>.sortByQueuedAt() = this.sortedByDescending { it.queuedAt }