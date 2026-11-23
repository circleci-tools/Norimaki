package com.unhappychoice.norimaki.presentation.presenter

import com.github.unhappychoice.circleci.v1.response.Build
import com.unhappychoice.norimaki.domain.model.BuildHistory
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

internal const val BUILD_HISTORY_LIMIT = 100

internal class BuildListRequests(
    private val io: Scheduler,
    private val computation: Scheduler,
    private val ui: Scheduler,
    private val fetchDetails: (Build) -> Observable<Build>,
    private var fetch: (Int) -> Observable<List<Build>>
) : Loadable, Paginatable {
    override val isLoading = Variable(false)
    override val page = Variable(0)
    override val hasMore = Variable(true)
    val canRetryPage = Variable(false)
    private val history = Variable(BuildHistory())
    val builds = history.asObservable().map { it.builds }

    private var initialLoadSettled = CompletableSubject.create()
    private var requestNumber = 0L
    private var buildPolling = BuildListPolling(fetch)
    var browsingOlderBuilds = false
        private set
    private var anchorOffset: Int? = null
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
        if (!initialLoadSettled.hasComplete() || isLoading.value || !hasMore.value) return null
        val current = history.value
        val tail = current.builds.lastOrNull()
        val startOffset = anchorOffset
        if (startOffset == null || tail == null) {
            return requestPage(page.value * BUILD_LIST_PAGE_SIZE).subscribeNext {}
        }

        var pageOffset = startOffset
        val source = fetchOlderBuilds(fetch, tail, current.ids, startOffset)
            .map { page ->
                pageOffset = page.offset
                page.builds
            }
        return request(source, paginated = true).subscribeNext { anchorOffset = pageOffset }
    }

    fun reset(fetch: (Int) -> Observable<List<Build>> = this.fetch) {
        this.fetch = fetch
        initialLoadSettled = CompletableSubject.create()
        buildPolling = BuildListPolling(fetch)
        lastPollIds = emptySet()
        browsingOlderBuilds = false
        anchorOffset = null
        latestRequestByBuild.clear()
        page.value = 0
        hasMore.value = true
        canRetryPage.value = false
        isLoading.value = false
        if (history.value.builds.isNotEmpty()) history.value = BuildHistory()
    }

    private fun pollLatest(): Observable<Unit> {
        if (page.value == 0) return if (isLoading.value) Observable.empty() else requestPage(0)

        var pollIds: Set<String>? = null
        val refreshed = buildPolling.fetchLatestBuilds(lastPollIds)
            .observeOn(ui)
            .doOnNext { latest -> pollIds = latest.take(BUILD_LIST_PAGE_SIZE).mapTo(hashSetOf()) { it.uniqueId() } }
            .concatMap { latest ->
                refreshBuildList(history.value.builds, latest) { build ->
                    Observable.defer { fetchDetails(build) }.subscribeOn(io)
                }
            }
        return request(refreshed, paginated = false) { pollIds.also { pollIds = null } }
    }

    private fun requestPage(offset: Int): Observable<Unit> {
        val fetchPage = fetch
        return request(Observable.defer { fetchPage(offset) }.singleOrError().toObservable(), paginated = true)
    }

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
        return response
            .doOnError {
                canRetryPage.value = true
                isLoading.value = false
            }
            .doOnDispose { isLoading.value = false }
    }

    private fun merge(response: Response): Single<Update> {
        val current = history.value
        val updatesOnly = !response.paginated && browsingOlderBuilds
        val eligible = response.builds.distinctBy { it.uniqueId() }.filter { build ->
            val id = build.uniqueId()
            val latest = latestRequestByBuild[id]
            (!updatesOnly || id in current.ids) && (latest == null || latest <= response.number)
        }
        return Single.fromCallable {
            val added = eligible.count { it.uniqueId() !in current.ids }
            val overflow = current.builds.size + added > BUILD_HISTORY_LIMIT
            val merged = current.merge(eligible, BUILD_HISTORY_LIMIT, keepOldest = response.paginated)
            Update(response, eligible, merged ?: current, added, overflow)
        }.subscribeOn(computation).observeOn(ui)
    }

    private fun publish(update: Update) {
        val response = update.response
        if (response.completion.isDisposed) return
        val current = history.value
        // A page in flight still needs the tail it started from.
        val deferNewBuilds = !response.paginated && update.overflow && isLoading.value
        val retained = if (deferNewBuilds) {
            current.merge(update.builds.filter { it.uniqueId() in current.ids }) ?: current
        } else {
            update.history
        }
        if (!deferNewBuilds) {
            if (response.paginated && update.overflow) browsingOlderBuilds = true
            if (update.overflow || (!response.paginated && update.added > 0)) {
                if (anchorOffset == null || !response.paginated && update.overflow) anchorOffset = 0
            }
            if (!response.paginated && update.overflow) hasMore.value = true
            response.pollIds?.let { lastPollIds = it }
        }
        latestRequestByBuild.keys.retainAll(retained.ids)
        update.builds.forEach {
            val id = it.uniqueId()
            if (id in retained.ids) latestRequestByBuild[id] = response.number
        }
        if (retained !== current) history.value = retained
        if (response.paginated) {
            if (page.value == 0) lastPollIds = response.builds.mapTo(hashSetOf()) { it.uniqueId() }
            hasMore.value = response.builds.isNotEmpty()
            page.value += 1
            canRetryPage.value = false
            isLoading.value = false
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

    private data class Update(
        val response: Response,
        val builds: List<Build>,
        val history: BuildHistory,
        val added: Int,
        val overflow: Boolean
    )
}
