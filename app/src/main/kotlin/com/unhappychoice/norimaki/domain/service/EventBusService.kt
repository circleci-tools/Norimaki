package com.unhappychoice.norimaki.domain.service

import io.reactivex.subjects.PublishSubject

class EventBusService {
    val authenticated: PublishSubject<Pair<String, String>> = PublishSubject.create()
    val unauthenticated: PublishSubject<Unit> = PublishSubject.create()
}
