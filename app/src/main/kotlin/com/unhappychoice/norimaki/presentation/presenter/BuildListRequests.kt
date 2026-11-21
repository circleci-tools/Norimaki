package com.unhappychoice.norimaki.presentation.presenter

import com.github.unhappychoice.circleci.v1.response.Build
import com.unhappychoice.norimaki.domain.model.BuildHistory
import com.unhappychoice.norimaki.domain.model.inWorkflow
import com.unhappychoice.norimaki.domain.model.refreshBuildList
import com.unhappychoice.norimaki.domain.model.uniqueId
import com.unhappychoice.norimaki.extension.Variable
import com.unhappychoice.norimaki.extension.polling
import com.unhappychoice.norimaki.extension.subscribeNext
import com.unhappychoice.norimaki.presentation.presenter.core.Loadable
import com.unhappychoice.norimaki.presentation.presenter.core.Paginatable
import io.reactivex.Observable
import io.reactivex.Scheduler
import io.reactivex.Single
import io.reactivex.SingleEmitter
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.subjects.CompletableSubject
import io.reactivex.subjects.PublishSubject
import java.util.concurrent.TimeUnit.SECONDS

internal class BuildListRequests(
    private val workflowId: String?,
    private val io: Scheduler,
    private val computation: Scheduler,
    private val ui: Scheduler,
    private val fetchDetails: (Build) -> Observable<Build>,
    private val fetch: (Int) -> Observable<List<Build>>
) : Loadable, Paginatable {
    override val isLoading = Variable(false)
    override val page = Variable(0)
    override val hasMore = Variable(true)
    val canRetryPage = Variable(false)
    private val history = Variable(BuildHistory())
    val builds = history.asObservable().map { it.builds }

    private var initialLoadSettled = CompletableSubject.create()
    private var requestNumber = 0L
    private var lastPollIds = emptySet<String>()
    private val latestRequestByBuild = mutableMapOf<String, Long>()
    private val incoming = PublishSubject.create<Response>().toSerialized()

    fun start(): Disposable = CompositeDisposable(
        incoming.concatMapSingle(::merge).subscribeNext(::publish),
        requestPage(0).doOnTerminate { initialLoadSettled.onComplete() }.subscribeNext {}
    )

    fun poll(period: Long, resuming: Boolean, clock: Scheduler): Disposable =
        initialLoadSettled.andThen(Observable.timer(if (resuming) 0L else period, SECONDS, clock))
            .flatMap {
                polling(period, scheduler = clock) {
                    Observable.just(Unit).observeOn(ui).flatMap { pollLatest() }
                }
            }
            .subscribeNext {}

    fun loadNextPage(): Disposable? {
        if (isLoading.value || !hasMore.value || (page.value == 0 && !canRetryPage.value)) return null
        return requestPage(page.value * BUILD_LIST_PAGE_SIZE).subscribeNext {}
    }

    fun reset() {
        initialLoadSettled = CompletableSubject.create()
        canRetryPage.value = false
        lastPollIds = emptySet()
        latestRequestByBuild.clear()
        page.value = 0
        hasMore.value = true
        isLoading.value = false
        if (history.value.builds.isNotEmpty()) history.value = BuildHistory()
    }

    private fun pollLatest(): Observable<Unit> {
        if (page.value == 0) return if (isLoading.value) Observable.empty() else requestPage(0)

        var pollIds: Set<String>? = null
        val refreshed = fetchLatestBuilds(lastPollIds, fetch)
            .observeOn(ui)
            .doOnNext { latest -> pollIds = latest.mapTo(hashSetOf()) { it.uniqueId() } }
            .concatMap { latest ->
                refreshBuildList(history.value.builds, latest, workflowId) { build ->
                    Observable.defer { fetchDetails(build) }.subscribeOn(io)
                }
            }
        return request(refreshed, paginated = false) { pollIds }
    }

    private fun requestPage(offset: Int): Observable<Unit> =
        request(Observable.defer { fetch(offset) }.singleOrError().toObservable(), paginated = true)

    private fun request(
        source: Observable<List<Build>>,
        paginated: Boolean,
        pollIds: () -> Set<String>? = { null }
    ): Observable<Unit> {
        val number = ++requestNumber
        val response = source
            .subscribeOn(io)
            .observeOn(ui)
            .concatMapSingle { builds ->
                Single.create<Unit> { completion ->
                    incoming.onNext(Response(number, builds, paginated, pollIds(), completion))
                }
            }
        if (!paginated) return response

        isLoading.value = true
        canRetryPage.value = false
        return response
            .doOnError {
                canRetryPage.value = !workflowId.isNullOrBlank()
                isLoading.value = false
            }
            .doOnDispose { isLoading.value = false }
    }

    private fun merge(response: Response): Single<Update> {
        val current = history.value
        val filter = workflowId.takeIf { response.paginated }
        val eligible = response.builds.inWorkflow(filter).filter { build ->
            val latest = latestRequestByBuild[build.uniqueId()]
            latest == null || latest <= response.number
        }
        return Single.fromCallable { Update(response, eligible, current.merge(eligible) ?: current) }
            .subscribeOn(computation)
            .observeOn(ui)
    }

    private fun publish(update: Update) {
        val response = update.response
        if (response.completion.isDisposed) return
        update.builds.forEach { latestRequestByBuild[it.uniqueId()] = response.number }
        if (update.history !== history.value) history.value = update.history
        response.pollIds?.let { lastPollIds = it }
        if (response.paginated) {
            if (page.value == 0) lastPollIds = response.builds.mapTo(hashSetOf()) { it.uniqueId() }
            hasMore.value = response.builds.isNotEmpty()
            isLoading.value = false
            page.value += 1
        }
        response.completion.onSuccess(Unit)
    }

    private data class Response(
        val number: Long,
        val builds: List<Build>,
        val paginated: Boolean,
        val pollIds: Set<String>?,
        val completion: SingleEmitter<Unit>
    )

    private data class Update(val response: Response, val builds: List<Build>, val history: BuildHistory)
}
