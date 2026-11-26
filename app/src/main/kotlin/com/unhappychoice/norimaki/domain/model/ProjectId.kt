package com.unhappychoice.norimaki.domain.model

data class ProjectId(val vcsType: String, val owner: String, val repository: String) {
    fun toPathString(): String = "$vcsType/$owner/$repository"
}
