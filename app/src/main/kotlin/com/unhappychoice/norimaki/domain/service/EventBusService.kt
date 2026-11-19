package com.unhappychoice.norimaki.domain.service

import com.github.unhappychoice.circleci.v1.response.Project
import com.gojuno.koptional.Optional
import io.reactivex.subjects.PublishSubject

class EventBusService {
    val selectProject: PublishSubject<Optional<Project>> = PublishSubject.create()
}
