package com.unhappychoice.norimaki.presentation.screen

import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.domain.model.BuildId
import com.unhappychoice.norimaki.presentation.screen.core.Screen
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.singleton

data class ArtifactsScreen(val buildId: BuildId) : Screen() {
    override fun getTitle(): String = "Artifacts · ${buildId.repository} #${buildId.number}"
    override fun getLayoutResource() = R.layout.artifacts_view

    override fun module(activityModule: DI) = DI {
        extend(activityModule)
        bind<BuildId>() with singleton { buildId }
    }
}
