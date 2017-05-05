package com.unhappychoice.norimaki.presentation.view.core

import android.content.Context
import com.unhappychoice.norimaki.ActivityComponent
import com.unhappychoice.norimaki.presentation.screen.core.Screen

interface UseComponent {
  fun getContext(): Context

  fun inject(screen: Screen) {
    val activityComponent = getContext().getSystemService(ActivityComponent.name) as ActivityComponent
    val component = screen.getSubComponent(activityComponent)
    val method = component.javaClass.getMethod("inject", this.javaClass)
    method.isAccessible = true
    method.invoke(component, this)
  }
}