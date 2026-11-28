package com.unhappychoice.norimaki.domain.service

import com.github.unhappychoice.circleci.CircleCIAPIClientV1_1
import com.github.unhappychoice.circleci.v1.response.Build
import com.github.unhappychoice.circleci.v1.response.User
import com.gojuno.koptional.None
import com.gojuno.koptional.Optional
import com.gojuno.koptional.Some
import com.unhappychoice.norimaki.domain.model.BuildId
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.CompletableSubject
import io.reactivex.subjects.ReplaySubject
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

class VerifiedSession(
    val token: String,
    val user: User,
    val client: CircleCIAPIClientV1_1
) {
    internal fun startRebuild(buildId: BuildId, clearCache: Boolean): Observable<Build>? {
        if (ended.hasComplete() || !pendingRebuilds.add(buildId)) return null
        // The session subscribes immediately, and the initiating view observes only the result.
        val result = ReplaySubject.createWithSize<Build>(1)
        Observable.defer {
            when {
                ended.hasComplete() -> Observable.empty<Build>()
                clearCache -> client.rebuildWithoutCache(buildId)
                else -> client.retryBuild(buildId.vcsType, buildId.owner, buildId.repository, buildId.number)
            }
        }
            .subscribeOn(Schedulers.io())
            .takeUntil(ended.toObservable<Unit>())
            .doOnError { it.printStackTrace() }
            .doOnTerminate { pendingRebuilds.remove(buildId) }
            .subscribe(result)
        return result.hide()
    }

    internal fun end() = ended.onComplete()

    internal fun workflowApprovals(workflowId: String): WorkflowApprovals =
        approvals.getOrPut(workflowId, ::WorkflowApprovals)

    internal fun releaseWorkflowApprovals(workflowId: String) {
        approvals.computeIfPresent(workflowId) { _, current -> current.takeUnless { it.isIdle() } }
    }

    internal val workflowApprovalCount: Int get() = approvals.size

    private val approvals = ConcurrentHashMap<String, WorkflowApprovals>()
    private val ended = CompletableSubject.create()
    private val pendingRebuilds = Collections.newSetFromMap(ConcurrentHashMap<BuildId, Boolean>())
}

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
            active.value?.toNullable()?.end()
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
