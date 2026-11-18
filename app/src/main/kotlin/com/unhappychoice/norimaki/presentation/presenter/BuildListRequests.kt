package com.unhappychoice.norimaki.presentation.presenter

import com.github.unhappychoice.circleci.v1.response.Build
import com.unhappychoice.norimaki.domain.model.BuildHistory
import com.unhappychoice.norimaki.domain.model.inWorkflow
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
import io.reactivex.subjects.PublishSubject

internal class BuildListRequests(
    private val workflowId: String?,
    private val io: Scheduler,
    private val computation: Scheduler,
    private val ui: Scheduler,
    private val fetch: (Int) -> Observable<List<Build>>
) : Loadable, Paginatable {
    override val isLoading = Variable(false)
    override val page = Variable(0)
    override val hasMore = Variable(true)
    private val history = Variable(BuildHistory())
    val builds = history.asObservable().map { it.builds }

    private var requestNumber = 0L
    private val latestRequestByBuild = mutableMapOf<String, Long>()
    private val incoming = PublishSubject.create<Response>().toSerialized()

    fun poll(period: Long): Disposable = CompositeDisposable(
        incoming.concatMapSingle(::merge).subscribeNext(::publish),
        polling(period, scheduler = ui) { request(offset = 0, paginated = page.value == 0) }
            .subscribeNext {}
    )

    fun loadNextPage(): Disposable? {
        if (page.value == 0 || isLoading.value || !hasMore.value) return null
        return request(offset = page.value * PAGE_SIZE, paginated = true).subscribeNext {}
    }

    private fun request(offset: Int, paginated: Boolean): Observable<Unit> {
        val number = ++requestNumber
        val source = Observable.defer { fetch(offset) }
        val response = (if (paginated) source.singleOrError().toObservable() else source)
            .subscribeOn(io)
            .observeOn(ui)
            .concatMapSingle { builds ->
                Single.create<Unit> { completion ->
                    incoming.onNext(Response(number, builds, paginated, completion))
                }
            }
        if (!paginated) return response

        isLoading.value = true
        return response
            .doOnError { isLoading.value = false }
            .doOnDispose { isLoading.value = false }
    }

    private fun merge(response: Response): Single<Update> {
        val current = history.value
        val eligible = response.builds.inWorkflow(workflowId).filter { build ->
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
        if (response.paginated) {
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
        val completion: SingleEmitter<Unit>
    )

    private data class Update(val response: Response, val builds: List<Build>, val history: BuildHistory)

    companion object {
        const val PAGE_SIZE = 20
    }
}
