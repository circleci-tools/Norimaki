package com.unhappychoice.norimaki.domain.service

import com.github.unhappychoice.circleci.response.Project
import com.gojuno.koptional.Optional
import io.reactivex.subjects.PublishSubject

class EventBusService {
    val authenticated: PublishSubject<Pair<String, String>> = PublishSubject.create()
    val unauthenticated: PublishSubject<Unit> = PublishSubject.create()
    val selectProject: PublishSubject<Optional<Project>> = PublishSubject.create()
}
