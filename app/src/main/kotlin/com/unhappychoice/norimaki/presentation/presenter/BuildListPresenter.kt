package com.unhappychoice.norimaki.presentation.presenter

import android.os.Bundle
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.github.unhappychoice.circleci.v1.response.Build
import com.unhappychoice.norimaki.domain.model.BuildId
import com.unhappychoice.norimaki.extension.goTo
import com.unhappychoice.norimaki.presentation.presenter.core.Loadable
import com.unhappychoice.norimaki.presentation.presenter.core.Paginatable
import com.unhappychoice.norimaki.presentation.presenter.core.PresenterNeedsToken
import com.unhappychoice.norimaki.presentation.screen.APITokenScreen
import com.unhappychoice.norimaki.presentation.screen.BuildScreen
import com.unhappychoice.norimaki.presentation.view.BuildListView
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.disposables.Disposables
import io.reactivex.disposables.SerialDisposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.schedulers.Schedulers
import org.kodein.di.instance

class BuildListPresenter : PresenterNeedsToken<BuildListView>(), Loadable, Paginatable {
    val projectName: String by instance()
    private val buildList by lazy {
        BuildListRequests(
            Schedulers.io(), Schedulers.computation(), AndroidSchedulers.mainThread(),
            ::getBuildDetails, ::getBuildsAPI
        )
    }
    private var pollingRegistration: Disposable? = null
    private var hasStartedPolling = false

    val builds get() = buildList.builds
    val canRetryPage get() = buildList.canRetryPage.asObservable()
    override val isLoading get() = buildList.isLoading
    override val page get() = buildList.page
    override val hasMore get() = buildList.hasMore

    override fun onLoad(savedInstanceState: Bundle?) {
        resetRequests()
        super.onLoad(savedInstanceState)
    }

    override fun onAuthenticated() {
        buildList.start().addTo(bag)
    }

    override fun onSessionEnded() {
        resetRequests()
    }

    fun getBuilds() {
        if (account != null) buildList.loadNextPage()?.addTo(bag)
    }

    override fun onViewDropped() {
        pollingRegistration = null
    }

    internal fun observePolling(lifecycle: Lifecycle = activity.lifecycle) {
        pollingRegistration?.let(bag::remove)
        val pollingSubscription = SerialDisposable()
        val observer = object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                val resuming = hasStartedPolling
                hasStartedPolling = true
                pollingSubscription.set(
                    buildList.poll(BUILD_LIST_POLLING_SECONDS, resuming, Schedulers.computation())
                )
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

    fun showLatestBuilds() {
        if (account == null || !buildList.browsingOlderBuilds) return
        bag.clear()
        buildList.reset()
        hasStartedPolling = false
        onAuthenticated()
        observePolling()
    }

    fun goToBuildView(build: Build) {
        if (account == null) return
        val buildId = BuildId.from(build) ?: return
        goTo(activity, BuildScreen(buildId))
    }

    fun changeAPIToken() {
        goTo(activity, APITokenScreen())
    }

    internal fun getBuildDetails(build: Build): Observable<Build> {
        val buildId = BuildId.from(build) ?: return Observable.empty()
        return Observable.defer {
            val client = account?.client ?: return@defer Observable.empty<Build>()
            buildId.load(client)
        }
    }

    private fun resetRequests() {
        hasStartedPolling = false
        buildList.reset()
    }

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
