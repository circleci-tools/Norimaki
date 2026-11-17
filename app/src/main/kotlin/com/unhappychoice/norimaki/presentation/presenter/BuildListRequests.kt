package com.unhappychoice.norimaki.presentation.presenter

import com.github.unhappychoice.circleci.v1.response.Build
import com.unhappychoice.norimaki.domain.model.addDistinctByNumber
import com.unhappychoice.norimaki.domain.model.inWorkflow
import com.unhappychoice.norimaki.domain.model.sortByQueuedAt
import com.unhappychoice.norimaki.domain.model.uniqueId
import com.unhappychoice.norimaki.extension.Variable
import com.unhappychoice.norimaki.extension.polling
import com.unhappychoice.norimaki.extension.subscribeNext
import com.unhappychoice.norimaki.presentation.presenter.core.Loadable
import com.unhappychoice.norimaki.presentation.presenter.core.Paginatable
import io.reactivex.Observable
import io.reactivex.Scheduler
import io.reactivex.disposables.Disposable

internal class BuildListRequests(
    private val workflowId: String?,
    private val io: Scheduler,
    private val ui: Scheduler,
    private val fetch: (Int) -> Observable<List<Build>>
) : Loadable, Paginatable {
    override val isLoading = Variable(false)
    override val page = Variable(0)
    override val hasMore = Variable(true)
    val builds = Variable<List<Build>>(emptyList())

    private var requestNumber = 0L
    private val latestRequestByBuild = mutableMapOf<String, Long>()

    fun poll(period: Long): Disposable =
        polling(period, scheduler = ui) { request(offset = 0, paginated = page.value == 0) }
            .subscribeNext(::merge)

    fun loadNextPage(): Disposable? {
        if (page.value == 0 || isLoading.value || !hasMore.value) return null
        return request(offset = page.value * PAGE_SIZE, paginated = true).subscribeNext(::merge)
    }

    private fun request(offset: Int, paginated: Boolean): Observable<Response> {
        val number = ++requestNumber
        val response = Observable.defer { fetch(offset) }.subscribeOn(io).observeOn(ui)
        return (if (paginated) response.startLoading().paginate() else response)
            .map { Response(number, it.inWorkflow(workflowId)) }
    }

    private fun merge(response: Response) {
        val updates = response.builds.filter { build ->
            val id = build.uniqueId()
            val latest = latestRequestByBuild[id]
            if (latest != null && latest > response.number) return@filter false
            latestRequestByBuild[id] = response.number
            true
        }
        builds.value = builds.value.addDistinctByNumber(updates).sortByQueuedAt()
    }

    private data class Response(val number: Long, val builds: List<Build>)

    companion object {
        const val PAGE_SIZE = 20
    }
}
