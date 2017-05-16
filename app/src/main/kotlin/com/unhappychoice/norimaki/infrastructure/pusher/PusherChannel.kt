package com.unhappychoice.norimaki.infrastructure.pusher

import com.pusher.client.Pusher
import com.pusher.client.PusherOptions
import com.pusher.client.util.HttpAuthorizer
import com.unhappychoice.norimaki.infrastructure.pusher.extension.presenceChannelEvents
import com.unhappychoice.norimaki.infrastructure.pusher.extension.privateChannelEvents
import io.reactivex.Observable

class PusherService {
    private var pusher: Pusher? = null

    fun connect(token: String) {
        close()
        pusher = Pusher(apiKey(), options(token)).apply { connect() }
    }

    fun subscribe(channelName: String, visibility: ChannelVisibility, eventName: String): Observable<String> =
        when (visibility) {
            is ChannelVisibility.Presence ->
                pusher?.subscribePresence(channelName, null)?.presenceChannelEvents(eventName)
            is ChannelVisibility.Private ->
                pusher?.subscribePrivate(channelName)?.privateChannelEvents(eventName)
            else ->
                pusher?.subscribePrivate(channelName)?.privateChannelEvents(eventName)
        } ?: Observable.empty()

    fun close() {
        pusher?.disconnect()
        pusher = null
    }

    private fun apiKey() = "1cf6e0e755e419d2ac9a"

    private fun options(token: String) =  PusherOptions().setAuthorizer(authorizer(token)).setEncrypted(true)

    private fun authorizer(token: String) = HttpAuthorizer("https://circleci.com/auth/pusher?circle-token=$token")
}
