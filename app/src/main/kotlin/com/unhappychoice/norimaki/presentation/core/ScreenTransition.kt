package com.unhappychoice.norimaki.presentation.core

import android.view.View
import android.view.ViewGroup
import flow.Direction

data class ScreenTransition(
    val incomingStartX: Float,
    val incomingStartAlpha: Float,
    val outgoingEndX: Float,
    val incomingOnTop: Boolean
) {
    fun indexIn(container: ViewGroup): Int = if (incomingOnTop) container.childCount else 0

    fun animate(outgoing: View, incoming: View, onEnd: () -> Unit) {
        incoming.translationX = incomingStartX
        incoming.alpha = incomingStartAlpha
        outgoing.animate().translationX(outgoingEndX).setDuration(DURATION_MILLIS).start()
        incoming.animate().translationX(0f).alpha(1f).setDuration(DURATION_MILLIS).withEndAction(onEnd).start()
    }

    companion object {
        const val DURATION_MILLIS = 250L
        private const val PARALLAX_RATIO = 0.3f

        fun of(direction: Direction, width: Float): ScreenTransition = when (direction) {
            Direction.FORWARD -> ScreenTransition(width, 1f, -width * PARALLAX_RATIO, true)
            Direction.BACKWARD -> ScreenTransition(-width * PARALLAX_RATIO, 1f, width, false)
            Direction.REPLACE -> ScreenTransition(0f, 0f, 0f, true)
        }
    }
}
