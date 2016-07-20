package com.unhappychoice.norimaki.screen.builds

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import javax.inject.Inject

class BuildsView(context: Context, attr: AttributeSet) : LinearLayout(context, attr) {
  @Inject lateinit var presenter: BuildsScreen.Presenter

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    presenter.takeView(this)
  }

  override fun onDetachedFromWindow() {
    presenter.dropView(this)
    super.onDetachedFromWindow()
  }
}