package com.unhappychoice.norimaki.presentation.presenter

import com.github.unhappychoice.circleci.v1.response.Build
import com.github.unhappychoice.circleci.v1.response.BuildAction
import com.github.unhappychoice.circleci.v1.response.BuildStep
import com.unhappychoice.norimaki.domain.model.findByName
import com.unhappychoice.norimaki.domain.model.isFinished
import com.unhappychoice.norimaki.domain.model.rawOutputUrl
import com.unhappychoice.norimaki.domain.model.vcsType
import com.unhappychoice.norimaki.extension.polling
import com.unhappychoice.norimaki.infrastructure.output.BuildOutputClient
import com.unhappychoice.norimaki.presentation.log.BuildLog
import com.unhappychoice.norimaki.presentation.presenter.core.PresenterNeedsToken
import com.unhappychoice.norimaki.presentation.view.BuildStepView
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.addTo
import io.reactivex.schedulers.Schedulers
import mortar.MortarScope
import org.kodein.di.instance

class BuildStepPresenter: PresenterNeedsToken<BuildStepView>() {
    val build: Build by instance()
    val buildStep: BuildStep by instance()
    private val outputClient: BuildOutputClient by instance()

    internal val log = BuildLog()
    @Volatile private var readBytes = 0L

    override fun onEnterScope(scope: MortarScope?) {
        super.onEnterScope(scope)
        when (buildStep.isFinished()) {
            true -> getActions()
            false -> buildStep.actions.firstOrNull()?.let { pollRunningAction(it) }
        }
    }

    private fun getActions() {
        val actions = buildStep.actions.mapNotNull { it.outputUrl }
        Observable.concat(actions.map { outputClient.fetchOutput(it) })
            .renderLog()
    }

    private fun pollRunningAction(action: BuildAction) {
        polling(LOG_POLLING_SECONDS) { fetchRawOutput(action) }
            .takeUntil(stepFinished())
            .concatWith(Observable.defer { fetchRawOutput(action) })
            .filter { it.isNotEmpty() }
            .doOnNext { readBytes += it.size }
            .map { String(it, Charsets.UTF_8) }
            .renderLog()
    }

    private fun fetchRawOutput(action: BuildAction): Observable<ByteArray> =
        outputClient.fetchRawOutput(build.rawOutputUrl(action), readBytes)

    private fun stepFinished(): Observable<Build> =
        polling(BuildPresenter.BUILD_POLLING_SECONDS) {
            api.getBuild(build.vcsType(), build.username!!, build.reponame!!, build.buildNum!!)
        }.filter { it.isFinished() || it.steps?.findByName(buildStep.name)?.isFinished() == true }

    private fun Observable<String>.renderLog() {
        val chunks = subscribeOn(Schedulers.io()).observeOn(Schedulers.computation())
        log.render(chunks, AndroidSchedulers.mainThread()).addTo(bag)
    }

    companion object {
        const val LOG_POLLING_SECONDS = 1L
    }
}
