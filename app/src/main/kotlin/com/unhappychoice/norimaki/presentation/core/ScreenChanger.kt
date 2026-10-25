package com.unhappychoice.norimaki.presentation.core

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.unhappychoice.norimaki.MainActivity
import com.unhappychoice.norimaki.presentation.screen.APITokenScreen
import com.unhappychoice.norimaki.presentation.screen.BuildListScreen
import com.unhappychoice.norimaki.presentation.screen.core.Screen
import com.unhappychoice.norimaki.presentation.view.core.BaseView
import flow.*

class ScreenChanger(val activity: MainActivity) : KeyChanger {
    private val containerView: ViewGroup
        get() = activity.binding.containerView

    private val contentView: View?
        get() = containerView.getChildAt(0)

    override fun changeKey(
        outgoingState: State?,
        incomingState: State,
        direction: Direction,
        incomingContexts: MutableMap<Any, Context>,
        callback: TraversalCallback
    ) {
        val outgoingView = contentView
        outgoingView?.let { outgoingState?.save(it) }

        val screen = incomingState.getScreen() ?: return callback.onTraversalCompleted()
        val incomingView = screen.inflateView().also { inject(screen, it) }
        val onCompleted = { completeTraversal(screen, callback) }

        outgoingView?.let { showWithTransition(it, incomingView, incomingState, direction, onCompleted) }
            ?: show(incomingView, incomingState, onCompleted)
    }

    private fun show(incomingView: View, incomingState: State, onCompleted: () -> Unit) {
        containerView.addView(incomingView)
        incomingState.restore(incomingView)
        onCompleted()
    }

    private fun showWithTransition(
        outgoingView: View,
        incomingView: View,
        incomingState: State,
        direction: Direction,
        onCompleted: () -> Unit
    ) {
        val transition = ScreenTransition.of(direction, containerView.width.toFloat())
        containerView.addView(incomingView, transition.indexIn(containerView))
        incomingState.restore(incomingView)
        transition.animate(outgoingView, incomingView) {
            containerView.removeView(outgoingView)
            onCompleted()
        }
    }

    private fun completeTraversal(screen: Screen, callback: TraversalCallback) {
        callback.onTraversalCompleted()
        updateActionBar(screen)
    }

    private fun updateActionBar(screen: Screen) {
        activity.drawerToggle.isDrawerIndicatorEnabled = showsDrawerIndicator(screen)
        activity.supportActionBar?.setHomeButtonEnabled(true)
        activity.supportActionBar?.setDisplayHomeAsUpEnabled(hasScreens())
        activity.supportActionBar?.setDisplayShowHomeEnabled(false)
        activity.supportActionBar?.title = screen.getTitle()
        activity.invalidateOptionsMenu()

        if (showsDrawerIndicator(screen)) { activity.drawerToggle.syncState() }
    }

    private fun showsDrawerIndicator(screen: Screen): Boolean = screen is BuildListScreen && !activity.isTwoPane

    private fun hasScreens(): Boolean =
        Flow.get(activity).history.asIterable().filter { it !is APITokenScreen }.size > 1

    private fun inject(screen: Screen, view: View) {
        try {
            (view as? BaseView<*>)?.let { baseView ->
                val module = screen.module(activity.module)
                baseView.di = module
                baseView.presenter.di = module
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun State.getScreen(): Screen? = getKey<Any?>() as? Screen

    private fun Screen.inflateView(): View =
        LayoutInflater.from(activity).inflate(getLayoutResource(), containerView, false)
}
