package com.unhappychoice.norimaki.domain.model

import com.github.unhappychoice.circleci.v1.response.Build

internal class BuildHistory private constructor(val builds: List<Build>) {
    constructor() : this(emptyList())

    private val buildsById = builds.associateBy { it.uniqueId() }

    fun merge(page: List<Build>): BuildHistory? {
        val distinctPage = page.distinctBy { it.uniqueId() }
        if (distinctPage.all { buildsById[it.uniqueId()] == it }) return null

        return BuildHistory(builds.addDistinctByNumber(distinctPage).sortByQueuedAt())
    }
}
