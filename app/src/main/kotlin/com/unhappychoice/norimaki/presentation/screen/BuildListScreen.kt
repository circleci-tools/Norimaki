package com.unhappychoice.norimaki.presentation.screen

import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.domain.model.ProjectId
import com.unhappychoice.norimaki.presentation.screen.core.Screen
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.singleton

class BuildListScreen(val projectId: ProjectId?, var selectedBranch: String = "") : Screen() {
    override fun getTitle(): String = projectId?.toPathString() ?: "Recent Builds"
    override fun getLayoutResource() = R.layout.build_list_view

    override fun module(activityModule: DI) = DI {
        extend(activityModule)
        projectId?.let { id -> bind<ProjectId>() with singleton { id } }
        bind<BuildListScreen>() with singleton { this@BuildListScreen }
    }

    override fun equals(other: Any?): Boolean {
        val os = other as? BuildListScreen
        if (os == null || os.projectId != projectId) {
            return false
        }
        return super.equals(other)
    }

    override fun hashCode(): Int {
        return projectId.hashCode()
    }
}
