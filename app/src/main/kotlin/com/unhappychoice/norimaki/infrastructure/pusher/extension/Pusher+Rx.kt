package com.unhappychoice.norimaki.infrastructure.pusher.extension

import android.util.Log
import com.pusher.client.channel.Channel
import com.pusher.client.channel.PrivateChannelEventListener
import com.unhappychoice.norimaki.extension.withLog
import io.reactivex.Observable

fun Channel.privateChannelEvents(eventName: String): Observable<String> {
  var listener: PrivateChannelEventListener?
  return Observable.create<String> { observer ->
    listener = object : PrivateChannelEventListener {
      override fun onAuthenticationFailure(message: String?, e: Exception?) {
        Log.e("Pusher", "Failed to authenticate $message ${e.toString()}")
      }

      override fun onSubscriptionSucceeded(channelName: String?) {
        Log.d("Pusher", "Succeeded to subscribe $channelName")
      }
      override fun onEvent(channelName: String?, eventName: String?, data: String?) {
        data?.let {
          Log.d("Pusher", "[$channelName:$eventName] $it")
          observer.onNext(it)
        }
      }
    }
    bind(eventName, listener)
  }.withLog("Pusher")
    .doOnError { listener = null }
    .doOnComplete { listener = null }
}
