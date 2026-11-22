package com.unhappychoice.norimaki.presentation.presenter

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.github.unhappychoice.circleci.v1.response.Build
import com.unhappychoice.norimaki.domain.model.BuildId
import com.unhappychoice.norimaki.extension.Variable
import com.unhappychoice.norimaki.extension.goTo
import com.unhappychoice.norimaki.extension.subscribeNext
import com.unhappychoice.norimaki.presentation.presenter.core.Loadable
import com.unhappychoice.norimaki.presentation.presenter.core.Paginatable
import com.unhappychoice.norimaki.presentation.presenter.core.PresenterNeedsToken
import com.unhappychoice.norimaki.presentation.screen.APITokenScreen
import com.unhappychoice.norimaki.presentation.screen.BuildScreen
import com.unhappychoice.norimaki.presentation.screen.WorkflowScreen
import com.unhappychoice.norimaki.presentation.view.BuildListView
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.disposables.Disposables
import io.reactivex.disposables.SerialDisposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.schedulers.Schedulers
import org.kodein.di.instance
import org.kodein.di.instanceOrNull

class BuildListPresenter : PresenterNeedsToken<BuildListView>(), Loadable, Paginatable {
    val projectName: String by instance()
    private val workflowId: String? by instanceOrNull(tag = WorkflowScreen.WORKFLOW_ID_TAG)
    private val buildList by lazy {
        BuildListRequests(
            workflowId, Schedulers.io(), Schedulers.computation(), AndroidSchedulers.mainThread(),
            ::getBuildDetails, projectBuilds(branch = null)
        )
    }
    private var pageRequests = CompositeDisposable()
    private var pollingLifecycle: Lifecycle? = null
    private var pollingRegistration: Disposable? = null
    private var hasStartedPolling = false

    val isWorkflow: Boolean get() = !workflowId.isNullOrBlank()
    val canFilterByBranch get() = !isWorkflow && projectName.isNotEmpty()
    private val branchSelection = Variable("")
    val selectedBranch = branchSelection.asObservable()
    var recentBranches: List<String> = emptyList()
        private set

    val builds get() = buildList.builds
    override val isLoading get() = buildList.isLoading
    override val page get() = buildList.page
    override val hasMore get() = buildList.hasMore
    val canRetryPage get() = buildList.canRetryPage

    override fun onAuthenticated() {
        pageRequests = CompositeDisposable().also { it.addTo(bag) }
        buildList.start().addTo(pageRequests)
        if (canFilterByBranch) {
            builds.subscribeNext { rows ->
                recentBranches = rows.asSequence()
                    .mapNotNull { it.branch?.takeIf(String::isNotBlank) }
                    .plus(recentBranches)
                    .distinct()
                    .take(BUILD_LIST_PAGE_SIZE)
                    .toList()
            }.addTo(bag)
        }
    }

    override fun onSessionEnded() {
        hasStartedPolling = false
        recentBranches = emptyList()
        branchSelection.value = ""
        buildList.reset(projectBuilds(branch = null))
    }

    fun getBuilds() {
        if (account != null) buildList.loadNextPage()?.addTo(pageRequests)
    }

    fun selectBranch(branch: String?) {
        val selection = branch.orEmpty()
        if (!canFilterByBranch || account == null || selection == branchSelection.value) return

        pollingRegistration?.let(bag::remove)
        pollingRegistration = null
        pageRequests.clear()
        buildList.reset(projectBuilds(branch?.takeUnless(String::isEmpty)))
        branchSelection.value = selection
        buildList.start().addTo(pageRequests)
        hasStartedPolling = false
        pollingLifecycle?.let { observePolling(it) }
    }

    fun getBuildsIfUnderfilled(canScroll: Boolean) {
        if (isWorkflow && !canScroll && !canRetryPage.value) getBuilds()
    }

    override fun onViewDropped() {
        pollingRegistration?.let(bag::remove)
        pollingRegistration = null
        pollingLifecycle = null
    }

    internal fun observePolling(lifecycle: Lifecycle = activity.lifecycle) {
        pollingLifecycle = lifecycle
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

    private fun projectBuilds(branch: String?): (Int) -> Observable<List<Build>> = fetch@{ offset ->
        val client = account?.client ?: return@fetch Observable.empty()
        when {
            projectName.isEmpty() -> client.getRecentBuilds(offset = offset, limit = BUILD_LIST_PAGE_SIZE)
            branch != null -> client.getBranchBuilds(
                vcsType = vcsType(),
                userName = userName(),
                project = repoName(),
                branch = branch,
                offset = offset,
                limit = BUILD_LIST_PAGE_SIZE
            )
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
