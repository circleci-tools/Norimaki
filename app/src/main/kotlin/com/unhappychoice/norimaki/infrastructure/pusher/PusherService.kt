package com.unhappychoice.norimaki.infrastructure.pusher

import com.pusher.client.Pusher
import com.pusher.client.PusherOptions
import com.pusher.client.util.HttpAuthorizer
import com.unhappychoice.norimaki.domain.service.EventBusService
import com.unhappychoice.norimaki.extension.bindTo
import com.unhappychoice.norimaki.extension.subscribeNext
import com.unhappychoice.norimaki.extension.withLog
import com.unhappychoice.norimaki.infrastructure.pusher.extension.privateChannelEvents
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo

class PusherService(val eventBus: EventBusService) {
  private var pusher: Pusher? = null
  private val bag = CompositeDisposable()
  private val subscriptionBag = CompositeDisposable()

  init {
    eventBus.authenticated
      .withLog("authenticated")
      .subscribeNext { connect(it.first, it.second) }
      .addTo(bag)

    eventBus.unauthenticated
      .subscribeNext { close() }
      .addTo(bag)
  }

  fun subscribe(channelName: String, eventName: String): Observable<String> =
    pusher?.subscribePrivate(channelName)
      ?.privateChannelEvents(eventName)
      ?.withLog(eventName)
      ?: Observable.empty<String>().withLog(eventName)

  private fun connect(token: String, pusherId: String) {
    close()
    pusher = Pusher(apiKey(), options(token)).apply { connect() }

    subscribe("private-$pusherId", "call")
      .map { Unit }
      .bindTo(eventBus.buildListUpdated)
      .addTo(subscriptionBag)
  }

  private fun close() {
    subscriptionBag.clear()
    pusher?.disconnect()
    pusher = null
  }

  private fun apiKey() = "1cf6e0e755e419d2ac9a"

  private fun options(token: String) = PusherOptions().setAuthorizer(authorizer(token)).setEncrypted(true)

  private fun authorizer(token: String) = HttpAuthorizer("https://circleci.com/auth/pusher?circle-token=$token")
}
