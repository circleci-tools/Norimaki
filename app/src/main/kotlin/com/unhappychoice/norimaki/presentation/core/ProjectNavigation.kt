package com.unhappychoice.norimaki.presentation.core

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
            val projectName = selection.toNullable()?.let { project ->
                "${vcsTypeFromUrl(project.vcsUrl)}/${project.username}/${project.reponame}"
            }.orEmpty()
            replaceTop(BuildListScreen(projectName), Direction.REPLACE)
        }
