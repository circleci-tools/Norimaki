package com.unhappychoice.norimaki.domain.model

import com.github.unhappychoice.circleci.CircleCIAPIClientV1_1
import com.github.unhappychoice.circleci.v1.response.Build

data class BuildId(val vcsType: String, val owner: String, val repository: String, val number: Int) {
    constructor(projectId: ProjectId, number: Int) : this(projectId.vcsType, projectId.owner, projectId.repository, number)

    val projectId: ProjectId get() = ProjectId(vcsType, owner, repository)

    fun load(api: CircleCIAPIClientV1_1) = api.getBuild(vcsType, owner, repository, number)

    companion object {
        fun from(build: Build): BuildId? {
            val owner = build.username ?: return null
            val repository = build.reponame ?: return null
            val number = build.buildNum ?: return null
            return BuildId(build.vcsType(), owner, repository, number)
        }
    }
}
