package com.unhappychoice.norimaki.presentation.view

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import com.unhappychoice.norimaki.ActivityComponent
import com.unhappychoice.norimaki.presentation.screen.BuildsScreen
import com.unhappychoice.norimaki.presentation.view.core.UseComponent
import javax.inject.Inject

class BuildsView(context: Context, attr: AttributeSet) : UseComponent, LinearLayout(context, attr) {
  @Inject lateinit var presenter: BuildsScreen.Presenter
  override fun useComponent(activityComponent: ActivityComponent) = activityComponent.buildsScreenComponent()

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    inject()
    presenter.takeView(this)
  }

  override fun onDetachedFromWindow() {
    presenter.dropView(this)
    super.onDetachedFromWindow()
  }
}