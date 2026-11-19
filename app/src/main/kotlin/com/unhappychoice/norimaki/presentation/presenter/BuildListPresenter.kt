package com.unhappychoice.norimaki.presentation.presenter

import com.github.unhappychoice.circleci.v1.response.Build
import com.unhappychoice.norimaki.domain.model.BuildHistory
import com.unhappychoice.norimaki.domain.model.inWorkflow
import com.unhappychoice.norimaki.domain.model.uniqueId
import com.unhappychoice.norimaki.extension.Variable
import com.unhappychoice.norimaki.extension.goTo
import com.unhappychoice.norimaki.extension.polling
import com.unhappychoice.norimaki.extension.subscribeNext
import com.unhappychoice.norimaki.extension.subscribeOnIoObserveOnUI
import com.unhappychoice.norimaki.presentation.presenter.core.Loadable
import com.unhappychoice.norimaki.presentation.presenter.core.Paginatable
import com.unhappychoice.norimaki.presentation.presenter.core.PresenterNeedsToken
import com.unhappychoice.norimaki.presentation.screen.APITokenScreen
import com.unhappychoice.norimaki.presentation.screen.BuildScreen
import com.unhappychoice.norimaki.presentation.screen.WorkflowScreen
import com.unhappychoice.norimaki.presentation.view.BuildListView
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.addTo
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import mortar.MortarScope
import org.kodein.di.instance
import org.kodein.di.instanceOrNull

class BuildListPresenter: PresenterNeedsToken<BuildListView>(), Loadable, Paginatable {
    override val isLoading = Variable(false)
    override val page = Variable(0)
    override val hasMore = Variable(true)
    val projectName: String by instance()
    private val workflowId: String? by instanceOrNull(tag = WorkflowScreen.WORKFLOW_ID_TAG)
    val isWorkflow: Boolean get() = !workflowId.isNullOrBlank()
    private val history = Variable(BuildHistory())
    private val incomingPages = PublishSubject.create<IncomingPage>().toSerialized()
    val builds = history.asObservable().map { it.builds }
    private val pageUpdates = observePages()

    override fun onEnterScope(scope: MortarScope?) {
        super.onEnterScope(scope)

        getBuilds()

        pollLatestBuilds()
    }

    override fun onExitScope() {
        pageUpdates.dispose()
        super.onExitScope()
    }

    fun getBuilds() {
        if (isLoading.value || !hasMore.value) return
        isLoading.value = true
        getBuildsAPI(offset = page.value * BUILD_LIST_PAGE_SIZE)
            .singleOrError()
            .toObservable()
            .subscribeOnIoObserveOnUI()
            .doOnError { isLoading.value = false }
            .doOnDispose { isLoading.value = false }
            .subscribeNext { incomingPages.onNext(IncomingPage(it, paginated = true)) }
            .addTo(bag)
    }

    fun getBuildsIfUnderfilled(canScroll: Boolean) {
        if (isWorkflow && !canScroll) getBuilds()
    }

    fun pollLatestBuilds() {
        Observable.defer {
            var lastPollIds: Set<String>? = null
            polling(BUILD_LIST_POLLING_SECONDS) {
                builds.take(1).flatMap { displayed ->
                    val knownIds = lastPollIds ?: displayed.mapTo(hashSetOf()) { it.uniqueId() }
                    fetchLatestBuilds(knownIds, ::getBuildsAPI)
                }.doOnNext { fetched -> lastPollIds = fetched.mapTo(hashSetOf()) { it.uniqueId() } }
            }
        }
            .subscribeOnIoObserveOnUI()
            .subscribeNext { incomingPages.onNext(IncomingPage(it, paginated = false)) }
            .addTo(bag)
    }

    fun goToBuildView(build: Build) {
        goTo(activity, BuildScreen(build))
    }

    fun changeAPIToken() {
        goTo(activity, APITokenScreen())
    }

    private fun observePages() = incomingPages.concatMapSingle { page ->
        val current = history.value
        val filter = workflowId
        Single.fromCallable { current.merge(page.builds.inWorkflow(filter)) ?: current }
            .subscribeOn(Schedulers.computation())
            .observeOn(AndroidSchedulers.mainThread())
            .map { updated -> page to updated }
    }.subscribeNext { (incoming, updated) ->
        if (updated !== history.value) history.value = updated
        if (incoming.paginated) {
            hasMore.value = incoming.builds.isNotEmpty()
            isLoading.value = false
            page.value += 1
        }
    }

    private data class IncomingPage(val builds: List<Build>, val paginated: Boolean)

    private fun getBuildsAPI(offset: Int): Observable<List<Build>> =
        when (projectName) {
            "" -> api.getRecentBuilds(offset = offset, limit = BUILD_LIST_PAGE_SIZE)
            else -> api.getProjectBuilds(
                vcsType = vcsType(),
                userName = userName(),
                project = repoName(),
                offset = offset,
                limit = BUILD_LIST_PAGE_SIZE
            )
        }

    private fun vcsType(): String = projectName.split("/").let {
        if (it.size >= 3) it[0] else "github"
    }
    private fun userName(): String = projectName.split("/").let {
        if (it.size >= 3) it[1] else it[0]
    }
    private fun repoName(): String = projectName.split("/").last()

    companion object {
        const val BUILD_LIST_POLLING_SECONDS = 10L
    }
}
