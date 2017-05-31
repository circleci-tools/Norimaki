package com.unhappychoice.norimaki.presentation.screen

import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.di.component.ActivityComponent
import com.unhappychoice.norimaki.presentation.screen.core.Screen

class BuildListScreen : Screen() {
    override fun getTitle(): String = "All builds"
    override fun getLayoutResource() = R.layout.build_list_view
    override fun getSubComponent(activityComponent: ActivityComponent) = activityComponent.buildListScreenComponent()
}