package com.unhappychoice.norimaki.infrastructure.pusher.extension

import android.util.Log
import com.pusher.client.channel.Channel
import com.pusher.client.channel.PresenceChannelEventListener
import com.pusher.client.channel.PrivateChannelEventListener
import com.pusher.client.channel.User
import io.reactivex.Observable

fun Channel.presenceChannelEvents(channelName: String): Observable<String> {
    var listener: PresenceChannelEventListener?
    return Observable.create<String> { observer ->
        listener = object : PresenceChannelEventListener {
            override fun onAuthenticationFailure(message: String?, e: Exception?) {
                Log.e("", "Failed to authenticate $message ${e.toString()}")
            }

            override fun onSubscriptionSucceeded(channelName: String?) {}

            override fun onUsersInformationReceived(channelName: String?, users: MutableSet<User>?) {}

            override fun userSubscribed(channelName: String?, user: User?) {}

            override fun userUnsubscribed(channelName: String?, user: User?) {}

            override fun onEvent(channelName: String?, eventName: String?, data: String?) {
                data?.let {
                    Log.d("", "[$channelName:$eventName] $it")
                    observer.onNext(it)
                }
            }
        }
        bind(channelName, listener)
    }
        .doOnError { listener = null }
        .doOnComplete { listener = null }
}

fun Channel.privateChannelEvents(channelName: String): Observable<String> {
    var listener: PrivateChannelEventListener?
    return Observable.create<String> { observer ->
        listener = object : PrivateChannelEventListener {
            override fun onAuthenticationFailure(message: String?, e: Exception?) {
                Log.e("", "Failed to authenticate $message ${e.toString()}")
            }
            override fun onSubscriptionSucceeded(channelName: String?) { }
            override fun onEvent(channelName: String?, eventName: String?, data: String?) {
                data?.let {
                    Log.d("", "[$channelName:$eventName] $it")
                    observer.onNext(it)
                }
            }
        }
        bind(channelName, listener)
    }
        .doOnError { listener = null }
        .doOnComplete { listener = null }
}
