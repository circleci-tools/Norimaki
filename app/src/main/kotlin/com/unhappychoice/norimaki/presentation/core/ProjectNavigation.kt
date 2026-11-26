package com.unhappychoice.norimaki.presentation.core

import com.unhappychoice.norimaki.domain.model.ProjectId
import com.unhappychoice.norimaki.domain.model.vcsTypeFromUrl
import com.unhappychoice.norimaki.domain.service.EventBusService
import com.unhappychoice.norimaki.extension.subscribeNext
import com.unhappychoice.norimaki.presentation.screen.BuildListScreen
import flow.Direction
import flow.Flow
import io.reactivex.disposables.Disposable

internal fun Flow.subscribeToProjectNavigation(
    eventBus: EventBusService,
    isTokenScreenVisible: () -> Boolean
): Disposable =
    eventBus.selectProject
        .filter { !isTokenScreenVisible() }
        .subscribeNext { selection ->
            val projectId = selection.toNullable()?.let { project ->
                ProjectId(vcsTypeFromUrl(project.vcsUrl), project.username.orEmpty(), project.reponame.orEmpty())
            }
            replaceHistory(BuildListScreen(projectId), Direction.REPLACE)
        }
