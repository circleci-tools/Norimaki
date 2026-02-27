package com.unhappychoice.norimaki.domain.service

import com.github.unhappychoice.circleci.v2.response.Collaboration
import com.gojuno.koptional.Optional
import io.reactivex.subjects.PublishSubject

class EventBusService {
    val authenticated: PublishSubject<String> = PublishSubject.create()
    val unauthenticated: PublishSubject<Unit> = PublishSubject.create()
    val selectProject: PublishSubject<Optional<Collaboration>> = PublishSubject.create()
}
