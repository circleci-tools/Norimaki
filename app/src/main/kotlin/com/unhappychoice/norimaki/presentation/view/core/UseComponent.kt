package com.unhappychoice.norimaki.presentation.view.core

import android.content.Context
import com.unhappychoice.norimaki.ActivityComponent

interface UseComponent {
  fun getContext(): Context
  fun useComponent(activityComponent: ActivityComponent): Any

  fun inject() {
    val activityComponent = getContext().getSystemService(ActivityComponent.name) as ActivityComponent
    val component = useComponent(activityComponent)
    val method = component.javaClass.getMethod("inject", this.javaClass)
    method.isAccessible = true
    method.invoke(component, this)
  }
}