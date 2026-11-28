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
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

class VerifiedSession(
    val token: String,
    val user: User,
    val client: CircleCIAPIClientV1_1
) {
    internal fun startRebuild(buildId: BuildId, clearCache: Boolean, cancel: Observable<*>): Observable<Build>? {
        if (!pendingRebuilds.add(buildId)) return null
        val release: () -> Unit = { pendingRebuilds.remove(buildId) }
        if (clearCache) return client.rebuildWithoutCache(buildId, cancel = cancel, onFinished = release)

        return Observable.defer {
            client.retryBuild(buildId.vcsType, buildId.owner, buildId.repository, buildId.number)
        }
            .subscribeOn(Schedulers.io())
            .takeUntil(cancel)
            .doOnError { it.printStackTrace() }
            .doFinally(release)
    }

    internal fun workflowApprovals(workflowId: String): WorkflowApprovals =
        approvals.getOrPut(workflowId, ::WorkflowApprovals)

    internal fun releaseWorkflowApprovals(workflowId: String) {
        approvals.computeIfPresent(workflowId) { _, current -> current.takeUnless { it.isIdle() } }
    }

    internal val workflowApprovalCount: Int get() = approvals.size

    private val approvals = ConcurrentHashMap<String, WorkflowApprovals>()
    private val pendingRebuilds = Collections.newSetFromMap(ConcurrentHashMap<BuildId, Boolean>())
}

class TokenSaveException(cause: Exception) : RuntimeException("Could not save the API token", cause)

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
            authenticate(token, ++attempt, save = true)
        }
    }

    private fun startRestore(): Single<VerifiedSession> {
        val token = savedToken
        val currentAttempt = ++attempt
        return authenticate(token, currentAttempt, save = false)
            .doOnEvent { _, _ ->
                synchronized(lock) {
                    if (currentAttempt == attempt) pendingRestore = null
                }
            }
            .cache()
            .also { pendingRestore = it }
    }

    private fun authenticate(token: String, currentAttempt: Int, save: Boolean): Single<VerifiedSession> = Single.defer {
        synchronized(lock) {
            check(currentAttempt == attempt) { "Authentication was superseded" }
            active.onNext(None)
        }
        require(token.isNotBlank()) { "Enter an API token" }
        val client = createClient(token)
        verify(client).map { user ->
            synchronized(lock) {
                check(currentAttempt == attempt) { "Authentication was superseded" }
                if (save) saveToken(token)
                VerifiedSession(token, user, client).also { active.onNext(Some(it)) }
            }
        }
    }

    private fun saveToken(token: String) {
        try {
            writeToken(token)
        } catch (e: Exception) {
            throw TokenSaveException(e)
        }
    }
}
