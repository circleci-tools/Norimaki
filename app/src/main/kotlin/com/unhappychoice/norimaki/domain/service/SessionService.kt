package com.unhappychoice.norimaki.domain.service

import com.github.unhappychoice.circleci.CircleCIAPIClientV1_1
import com.github.unhappychoice.circleci.v1.response.User
import com.gojuno.koptional.None
import com.gojuno.koptional.Optional
import com.gojuno.koptional.Some
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.subjects.BehaviorSubject

class VerifiedSession(
    val token: String,
    val user: User,
    val client: CircleCIAPIClientV1_1
)

class SessionService(
    private val readToken: () -> String,
    private val writeToken: (String) -> Unit,
    private val createClient: (String) -> CircleCIAPIClientV1_1,
    private val verify: (CircleCIAPIClientV1_1) -> Single<User>
) {
    private val active = BehaviorSubject.createDefault<Optional<VerifiedSession>>(None)
    private val lock = Any()
    private var attempt = 0
    private var pendingRestore: Single<VerifiedSession>? = null

    val changes: Observable<Optional<VerifiedSession>> = active.hide()
    val hasVerifiedSession: Boolean get() = active.value?.toNullable() != null
    val savedToken: String get() = readToken()

    fun restore(): Single<VerifiedSession> = Single.defer {
        synchronized(lock) {
            active.value?.toNullable()?.let { Single.just(it) } ?: pendingRestore ?: startRestore()
        }
    }

    fun replace(token: String): Single<VerifiedSession> = Single.defer {
        synchronized(lock) {
            pendingRestore = null
            authenticate(token, ++attempt)
        }
    }

    private fun startRestore(): Single<VerifiedSession> {
        val token = savedToken
        val currentAttempt = ++attempt
        return authenticate(token, currentAttempt)
            .doOnEvent { _, _ ->
                synchronized(lock) {
                    if (currentAttempt == attempt) pendingRestore = null
                }
            }
            .cache()
            .also { pendingRestore = it }
    }

    private fun authenticate(token: String, currentAttempt: Int): Single<VerifiedSession> = Single.defer {
        synchronized(lock) {
            check(currentAttempt == attempt) { "Authentication was superseded" }
            active.onNext(None)
        }
        require(token.isNotBlank()) { "Enter an API token" }
        val client = createClient(token)
        verify(client).map { user ->
            synchronized(lock) {
                check(currentAttempt == attempt) { "Authentication was superseded" }
                VerifiedSession(token, user, client).also {
                    writeToken(token)
                    active.onNext(Some(it))
                }
            }
        }
    }
}
