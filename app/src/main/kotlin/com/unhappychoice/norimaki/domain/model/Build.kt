package com.unhappychoice.norimaki.domain.model

import com.github.unhappychoice.circleci.v1.response.Build
import com.github.unhappychoice.circleci.v1.response.BuildAction
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

fun Build.repositoryString(): String = "$username / $reponame"
fun Build.revisionString(): String = "$branch #$buildNum (${vcsRevision?.take(6)})"

fun Build.buildStatus(): BuildStatus {
    val effectiveStatus = if (status == "retried" && lifecycle == "finished") outcome else status
    return when (effectiveStatus) {
        "success", "fixed", "no_tests" -> BuildStatus.SUCCESS
        "canceled" -> BuildStatus.CANCELED
        "infrastructure_fail", "timedout", "failed" -> BuildStatus.FAILED
        "not_run" -> BuildStatus.TERMINAL
        else -> if (isFinished()) BuildStatus.TERMINAL else BuildStatus.ACTIVE
    }
}

fun Build.avatarUrl(): String? {
    val author = user ?: return null
    val login = (author["login"] as? String)?.takeIf { it.isNotBlank() } ?: return null
    val provider = vcsType()
    if (provider == "github") return "https://github.com/$login.png"
    if (author["vcs_type"]?.let { it != provider } == true) return null

    return (author["avatar_url"] as? String)?.toHttpUrlOrNull()
        ?.takeUnless { url -> githubAvatarDomains.any { url.host == it || url.host.endsWith(".$it") } }
        ?.toString()
}

fun Build.repositoryWithWorkflowString(): String =
    listOfNotNull(repositoryString(), workflowString()).joinToString("  ·  ")

fun Build.workflowString(): String? =
    listOfNotNull(workflows?.workflowName, workflows?.jobName).joinToString(" / ").ifEmpty { null }

fun Build.projectName(): String = "${vcsType()}/$username/$reponame"

fun Build.projectId(): ProjectId = ProjectId(vcsType(), username.orEmpty(), reponame.orEmpty())

fun Build.uniqueId(): String = "${projectName()}/$buildNum"

fun Build.isFinished(): Boolean = lifecycle in listOf("finished", "not_run")

fun Build.rawOutputUrl(action: BuildAction): String =
    "https://circleci.com/api/private/output/raw/${vcsType()}/$username/$reponame/$buildNum/output/${action.index}/${action.step}"

fun Build.vcsType(): String = vcsTypeFromUrl(vcsUrl)

fun vcsTypeFromUrl(url: String?): String = when (url?.toHttpUrlOrNull()?.host) {
    "bitbucket.org" -> "bitbucket"
    else -> "github"
}

fun List<Build>.addDistinctByNumber(builds: List<Build>) = (builds + this).distinctBy { it.uniqueId() }
fun List<Build>.sortByQueuedAt() = this.sortedByDescending { it.queuedAt }

private val githubAvatarDomains = listOf("github.com", "githubusercontent.com")
