package com.unhappychoice.norimaki.presentation.presenter

import com.github.unhappychoice.circleci.v1.response.Build
import com.unhappychoice.norimaki.domain.model.BuildHistory
import com.unhappychoice.norimaki.domain.model.inWorkflow
import com.unhappychoice.norimaki.domain.model.vcsTypeFromUrl
import com.unhappychoice.norimaki.extension.Variable
import com.unhappychoice.norimaki.extension.goTo
import com.unhappychoice.norimaki.extension.polling
import com.unhappychoice.norimaki.extension.subscribeNext
import com.unhappychoice.norimaki.extension.subscribeOnIoObserveOnUI
import com.unhappychoice.norimaki.presentation.presenter.core.Loadable
import com.unhappychoice.norimaki.presentation.presenter.core.Paginatable
import com.unhappychoice.norimaki.presentation.presenter.core.PresenterNeedsToken
import com.unhappychoice.norimaki.presentation.screen.APITokenScreen
import com.unhappychoice.norimaki.presentation.screen.BuildListScreen
import com.unhappychoice.norimaki.presentation.screen.BuildScreen
import com.unhappychoice.norimaki.presentation.screen.WorkflowScreen
import com.unhappychoice.norimaki.presentation.view.BuildListView
import flow.Direction
import flow.Flow
import io.reactivex.Maybe
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
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
    private val history = Variable(BuildHistory())
    private val incomingPages = PublishSubject.create<List<Build>>()
    val builds = history.asObservable().map { it.builds }
    private var pageUpdates: Disposable? = null

    override fun onEnterScope(scope: MortarScope?) {
        super.onEnterScope(scope)
        pageUpdates = observePages()

        getBuilds()

        pollLatestBuilds()

        eventBus.selectProject
            .map { optional ->
                val project = optional.toNullable()
                    ?: return@map ""
                "${vcsTypeFromUrl(project.vcsUrl)}/${project.username}/${project.reponame}"
            }
            .subscribeNext { goToBuildListView(it) }
            .addTo(bag)
    }

    override fun onExitScope() {
        pageUpdates?.dispose()
        super.onExitScope()
    }

    fun getBuilds() {
        if (isLoading.value || !hasMore.value) return
        getBuildsAPI(offset = this.page.value * 20)
            .subscribeOnIoObserveOnUI()
            .startLoading()
            .paginate()
            .subscribeNext { incomingPages.onNext(it) }
            .addTo(bag)
    }

    fun pollLatestBuilds() {
        polling(BUILD_LIST_POLLING_SECONDS) { getBuildsAPI(offset = 0) }
            .subscribeOnIoObserveOnUI()
            .subscribeNext { incomingPages.onNext(it) }
            .addTo(bag)
    }

    fun goToBuildView(build: Build) {
        goTo(activity, BuildScreen(build))
    }

    fun changeAPIToken() {
        goTo(activity, APITokenScreen())
    }

    private fun observePages(): Disposable =
        incomingPages.concatMapMaybe { page ->
            val current = history.value
            val filter = workflowId
            Maybe.defer<BuildHistory> {
                val updated = current.merge(page.inWorkflow(filter))
                if (updated == null) Maybe.empty() else Maybe.just(updated)
            }
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
        }.subscribeNext { history.value = it }

    private fun goToBuildListView(projectName: String) {
        Flow.get(activity).replaceTop(BuildListScreen(projectName), Direction.REPLACE)
    }

    private fun getBuildsAPI(offset: Int): Observable<List<Build>> =
        when (projectName) {
            "" -> api.getRecentBuilds(offset = offset, limit = 20)
            else -> api.getProjectBuilds(
                vcsType = vcsType(),
                userName = userName(),
                project = repoName(),
                offset = offset,
                limit = 20
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
