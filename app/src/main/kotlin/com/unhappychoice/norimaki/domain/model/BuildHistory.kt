package com.unhappychoice.norimaki.domain.model

import com.github.unhappychoice.circleci.v1.response.Build

internal class BuildHistory private constructor(val builds: List<Build>) {
    constructor() : this(emptyList())

    private val buildsById = builds.associateBy { it.uniqueId() }

    val ids: Set<String> get() = buildsById.keys

    fun merge(
        page: List<Build>,
        limit: Int = Int.MAX_VALUE,
        keepOldest: Boolean = false
    ): BuildHistory? {
        val distinctPage = page.distinctBy { it.uniqueId() }
        if (distinctPage.all { buildsById[it.uniqueId()] == it }) return null

        val updates = distinctPage.associateBy { it.uniqueId() }
        val retained = builds.map { updates[it.uniqueId()] ?: it }
        val added = distinctPage.filter { it.uniqueId() !in ids }
        val combined = if (keepOldest) retained + added else added + retained
        val sorted = combined.sortByQueuedAt()
        val window = if (keepOldest) sorted.takeLast(limit) else sorted.take(limit)
        return if (window == builds) null else BuildHistory(window)
    }
}
