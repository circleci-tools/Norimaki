package com.unhappychoice.norimaki.infrastructure.pusher.extension

import android.util.Log
import com.pusher.client.channel.Channel
import com.pusher.client.channel.PrivateChannelEventListener
import io.reactivex.Observable

fun Channel.privateChannelEvents(eventName: String): Observable<String> {
    var listener: PrivateChannelEventListener? = null
    return Observable.create<String> { observer ->
        listener = object : PrivateChannelEventListener {
            override fun onAuthenticationFailure(message: String?, e: Exception?) {
                Log.e("Pusher", "Failed to authenticate $message ${e.toString()}")
            }

            override fun onSubscriptionSucceeded(channelName: String?) {
                Log.d("Pusher", "Succeeded to subscribe $channelName")
            }

            override fun onEvent(channelName: String?, eventName: String?, data: String?) {
                data?.let { observer.onNext(it) }
            }
        }
        bind(eventName, listener)
    }
        .doOnError {
            unbind(eventName, listener)
            listener = null
        }
        .doOnComplete {
            unbind(eventName, listener)
            listener = null
        }
}
