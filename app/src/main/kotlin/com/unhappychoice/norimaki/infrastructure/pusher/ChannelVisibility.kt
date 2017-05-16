package com.unhappychoice.norimaki.infrastructure.pusher

sealed class ChannelVisibility {
    object Presence : ChannelVisibility()
    object Private : ChannelVisibility()
}
