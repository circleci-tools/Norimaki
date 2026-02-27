package com.unhappychoice.norimaki.infrastructure.pusher

import com.unhappychoice.norimaki.domain.service.EventBusService
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.subjects.PublishSubject

// Pusher integration is no longer supported with CircleCI API V2.
// This class is kept as a stub for potential future real-time update support.
class PusherService(eventBus: EventBusService) {
    val buildListUpdated: PublishSubject<Unit> = PublishSubject.create()
    private val bag = CompositeDisposable()
}
