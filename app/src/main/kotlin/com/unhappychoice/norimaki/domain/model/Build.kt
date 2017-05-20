package com.unhappychoice.norimaki.domain.model

import android.graphics.Color
import com.github.unhappychoice.circleci.response.Build

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

fun Build.channelName(): String = "private-$username@$reponame@$buildNum@vcs-github@0"

fun List<Build>.addDistinctByNumber(builds: List<Build>) = (builds + this).distinctBy { it.uniqueId() }
fun List<Build>.sortByQueuedAt() = this.sortedByDescending { it.queuedAt }