package com.unhappychoice.norimaki.presentation.presenter

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.github.unhappychoice.circleci.v1.response.Build
import com.unhappychoice.norimaki.domain.model.BuildHistory
import com.unhappychoice.norimaki.domain.model.BuildId
import com.unhappychoice.norimaki.domain.model.inWorkflow
import com.unhappychoice.norimaki.domain.model.refreshBuildList
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
import io.reactivex.disposables.Disposable
import io.reactivex.disposables.Disposables
import io.reactivex.disposables.SerialDisposable
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
    private var lastPollIds = emptySet<String>()
    private val incomingPages = PublishSubject.create<IncomingPage>().toSerialized()
    private var pollingRegistration: Disposable? = null
    private var hasStartedPolling = false
    val builds = history.asObservable().map { it.builds }

    override fun onAuthenticated() {
        observePages().addTo(bag)
        getBuilds()
    }

    override fun onSessionEnded() {
        initialLoadSettled = CompletableSubject.create()
        lastPollIds = emptySet()
        hasStartedPolling = false
        isLoading.value = false
        history.value = BuildHistory()
        hasMore.value = true
        page.value = 0
    }

    fun getBuilds() {
        loadPage()
            .subscribeNext(incomingPages::onNext)
            .addTo(bag)
    }

    fun getBuildsIfUnderfilled(canScroll: Boolean) {
        if (isWorkflow && !canScroll) getBuilds()
    }

    override fun onViewDropped() {
        pollingRegistration?.let(bag::remove)
        pollingRegistration = null
    }

    internal fun observePolling(lifecycle: Lifecycle = activity.lifecycle) {
        pollingRegistration?.let(bag::remove)
        val pollingSubscription = SerialDisposable()
        val observer = object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                val resuming = hasStartedPolling
                hasStartedPolling = true
                pollingSubscription.set(pollLatestBuilds(resuming))
            }

            override fun onStop(owner: LifecycleOwner) {
                pollingSubscription.set(null)
            }
        }
        val registration = Disposables.fromAction {
            lifecycle.removeObserver(observer)
            pollingSubscription.dispose()
        }
        if (bag.add(registration)) {
            pollingRegistration = registration
            lifecycle.addObserver(observer)
        }
    }

    fun goToBuildView(build: Build) {
        if (account == null) return
        val buildId = BuildId.from(build) ?: return
        goTo(activity, BuildScreen(buildId))
    }

    fun changeAPIToken() {
        goTo(activity, APITokenScreen())
    }

    private fun loadPage(): Observable<IncomingPage> {
        if (account == null || isLoading.value || !hasMore.value) return Observable.empty()
        isLoading.value = true
        return getBuildsAPI(offset = page.value * BUILD_LIST_PAGE_SIZE)
            .singleOrError()
            .toObservable()
            .subscribeOnIoObserveOnUI()
            .doOnError {
                isLoading.value = false
                initialLoadSettled.onComplete()
            }
            .doOnDispose { isLoading.value = false }
            .map { IncomingPage(it, paginated = true) }
    }

    private fun pollLatestBuilds(resuming: Boolean): Disposable {
        val start = if (resuming) {
            Observable.just(0L)
        } else {
            Observable.timer(BUILD_LIST_POLLING_SECONDS, TimeUnit.SECONDS)
        }
        return initialLoadSettled.andThen(start)
            .flatMap { polling(BUILD_LIST_POLLING_SECONDS, fetch = ::refreshBuilds) }
            .subscribeNext(incomingPages::onNext)
    }

    private fun refreshBuilds(): Observable<IncomingPage> = Observable.just(Unit)
        .observeOn(AndroidSchedulers.mainThread())
        .flatMap {
            if (page.value == 0) {
                loadPage()
            } else {
                fetchLatestBuilds(lastPollIds, ::getBuildsAPI)
                    .subscribeOnIoObserveOnUI()
                    .concatMap { latest ->
                        val pollIds = latest.mapTo(hashSetOf()) { it.uniqueId() }
                        refreshBuildList(history.value.builds, latest, workflowId, ::getBuildDetails)
                            .map { IncomingPage(it, paginated = false, pollIds = pollIds) }
                    }
            }
        }

    internal fun getBuildDetails(build: Build): Observable<Build> {
        val buildId = BuildId.from(build) ?: return Observable.empty()
        return Observable.defer {
            val client = account?.client ?: return@defer Observable.empty<Build>()
            buildId.load(client)
        }.subscribeOnIoObserveOnUI()
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
        incoming.pollIds?.let { lastPollIds = it }
        if (incoming.paginated) {
            if (page.value == 0) lastPollIds = incoming.builds.mapTo(hashSetOf()) { it.uniqueId() }
            hasMore.value = incoming.builds.isNotEmpty()
            isLoading.value = false
            page.value += 1
            initialLoadSettled.onComplete()
        }
    }

    private data class IncomingPage(
        val builds: List<Build>,
        val paginated: Boolean,
        val pollIds: Set<String>? = null
    )

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
