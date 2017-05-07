package com.unhappychoice.norimaki.presentation.core

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.unhappychoice.norimaki.ActivityComponent
import com.unhappychoice.norimaki.MainActivity
import com.unhappychoice.norimaki.presentation.screen.core.Screen
import flow.Direction
import flow.KeyChanger
import flow.State
import flow.TraversalCallback
import kotlinx.android.synthetic.main.activity_main.*

class ScreenChanger(val activity: MainActivity) : KeyChanger {
  private val containerView: ViewGroup
    get() = activity.containerView

  private val contentView: View
    get() = containerView.getChildAt(0)

  override fun changeKey(
    outgoingState: State?,
    incomingState: State,
    direction: Direction,
    incomingContexts: MutableMap<Any, Context>,
    callback: TraversalCallback
  ) {
    outgoingState?.save(contentView)
    containerView.removeAllViews()

    val screen = incomingState.getScreen()

    screen?.inflateView()?.let {
      screen.injectPresenter(it)
      containerView.addView(it)
      incomingState.restore(it)
    }
    callback.onTraversalCompleted()
  }

  private fun Screen.injectPresenter(view: View) {
    try {
      val activityComponent = activity.getSystemService(ActivityComponent.name) as ActivityComponent
      val screenComponent = getSubComponent(activityComponent)
      screenComponent.javaClass.getMethod("inject", view.javaClass).apply {
        isAccessible = true
        invoke(screenComponent, view)
      }
    } catch (e: Exception) {
      e.printStackTrace()
    }
  }

  private fun State.getScreen(): Screen? = getKey<Any?>() as? Screen

  private fun Screen.inflateView(): View =
      LayoutInflater.from(activity).inflate(getLayoutResource(), containerView, false)
}
