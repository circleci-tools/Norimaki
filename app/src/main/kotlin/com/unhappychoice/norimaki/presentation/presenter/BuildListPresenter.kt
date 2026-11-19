package com.unhappychoice.norimaki.presentation.presenter

import com.github.unhappychoice.circleci.v1.response.Build
import com.unhappychoice.norimaki.domain.model.BuildHistory
import com.unhappychoice.norimaki.domain.model.inWorkflow
import com.unhappychoice.norimaki.domain.model.refreshBuildList
import com.unhappychoice.norimaki.domain.model.vcsType
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
import io.reactivex.subjects.CompletableSubject
import io.reactivex.subjects.PublishSubject
import org.kodein.di.instance
import org.kodein.di.instanceOrNull
import java.util.concurrent.TimeUnit

class BuildListPresenter: PresenterNeedsToken<BuildListView>(), Loadable, Paginatable {
    override val isLoading = Variable(false)
    override val page = Variable(0)
    override val hasMore = Variable(true)
    val projectName: String by instance()
    private val workflowId: String? by instanceOrNull(tag = WorkflowScreen.WORKFLOW_ID_TAG)
    val isWorkflow: Boolean get() = !workflowId.isNullOrBlank()
    private val history = Variable(BuildHistory())
    private var initialLoadSettled = CompletableSubject.create()
    private var initialPage = emptyList<Build>()
    private val incomingPages = PublishSubject.create<IncomingPage>().toSerialized()
    val builds = history.asObservable().map { it.builds }

    override fun onAuthenticated() {
        initialLoadSettled = CompletableSubject.create()
        initialPage = emptyList()
        observePages().addTo(bag)
        startBuilds()
    }

    override fun onSessionEnded() {
        isLoading.value = false
        history.value = BuildHistory()
        hasMore.value = true
        page.value = 0
    }

    fun getBuilds() {
        if (account == null || isLoading.value || !hasMore.value) return
        isLoading.value = true
        getBuildsAPI(offset = page.value * BUILD_LIST_PAGE_SIZE)
            .singleOrError()
            .toObservable()
            .subscribeOnIoObserveOnUI()
            .doOnError {
                isLoading.value = false
                initialLoadSettled.onComplete()
            }
            .doOnDispose { isLoading.value = false }
            .subscribeNext { incomingPages.onNext(IncomingPage(it, paginated = true)) }
            .addTo(bag)
    }

    fun getBuildsIfUnderfilled(canScroll: Boolean) {
        if (isWorkflow && !canScroll) getBuilds()
    }

    fun goToBuildView(build: Build) {
        if (account == null) return
        goTo(activity, BuildScreen(build))
    }

    fun changeAPIToken() {
        goTo(activity, APITokenScreen())
    }

    private fun startBuilds() {
        getBuilds()
        initialLoadSettled
            .andThen(Observable.timer(BUILD_LIST_POLLING_SECONDS, TimeUnit.SECONDS))
            .flatMap { polling(BUILD_LIST_POLLING_SECONDS, fetch = ::refreshBuilds) }
            .subscribeNext { incomingPages.onNext(IncomingPage(it, paginated = false)) }
            .addTo(bag)
    }

    private fun refreshBuilds(): Observable<List<Build>> = Observable.just(Unit)
        .observeOn(AndroidSchedulers.mainThread())
        .flatMap {
            if (page.value == 0) {
                getBuilds()
                Observable.empty()
            } else {
                fetchLatestBuilds(history.value.builds + initialPage, ::getBuildsAPI)
                    .subscribeOnIoObserveOnUI()
                    .concatMap { latest -> refreshBuildList(history.value.builds, latest, workflowId, ::getBuildDetails) }
            }
        }

    private fun getBuildDetails(build: Build): Observable<Build> {
        val username = build.username ?: return Observable.empty()
        val repository = build.reponame ?: return Observable.empty()
        val number = build.buildNum ?: return Observable.empty()
        val client = account?.client ?: return Observable.empty()
        return Observable.defer { client.getBuild(build.vcsType(), username, repository, number) }
            .subscribeOnIoObserveOnUI()
    }

    private fun observePages() = incomingPages.concatMapSingle { page ->
        val current = history.value
        val filter = workflowId.takeIf { page.paginated }
        Single.fromCallable { current.merge(page.builds.inWorkflow(filter)) ?: current }
            .subscribeOn(Schedulers.computation())
            .observeOn(AndroidSchedulers.mainThread())
            .map { updated -> page to updated }
    }.subscribeNext { (incoming, updated) ->
        if (updated !== history.value) history.value = updated
        if (incoming.paginated) {
            if (page.value == 0) initialPage = incoming.builds
            hasMore.value = incoming.builds.isNotEmpty()
            isLoading.value = false
            page.value += 1
            initialLoadSettled.onComplete()
        }
    }

    private data class IncomingPage(val builds: List<Build>, val paginated: Boolean)

    private fun getBuildsAPI(offset: Int): Observable<List<Build>> {
        val client = account?.client ?: return Observable.empty()
        return when (projectName) {
            "" -> client.getRecentBuilds(offset = offset, limit = BUILD_LIST_PAGE_SIZE)
            else -> client.getProjectBuilds(
                vcsType = vcsType(),
                userName = userName(),
                project = repoName(),
                offset = offset,
                limit = BUILD_LIST_PAGE_SIZE
            )
        }
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
