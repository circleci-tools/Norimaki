package com.unhappychoice.norimaki.presentation.presenter

import com.github.unhappychoice.circleci.v1.response.Build
import com.github.unhappychoice.circleci.v1.response.BuildAction
import com.github.unhappychoice.circleci.v1.response.BuildStep
import com.unhappychoice.norimaki.domain.model.findByStepNumber
import com.unhappychoice.norimaki.domain.model.isFinished
import com.unhappychoice.norimaki.domain.model.rawOutputUrl
import com.unhappychoice.norimaki.domain.model.vcsType
import com.unhappychoice.norimaki.extension.*
import com.unhappychoice.norimaki.infrastructure.output.BuildOutputClient
import com.unhappychoice.norimaki.presentation.presenter.core.PresenterNeedsToken
import com.unhappychoice.norimaki.presentation.view.BuildStepView
import io.reactivex.Observable
import io.reactivex.rxkotlin.addTo
import mortar.MortarScope
import org.kodein.di.instance

class BuildStepPresenter: PresenterNeedsToken<BuildStepView>() {
    val build: Build by instance()
    val buildStep: BuildStep by instance()
    private val outputClient: BuildOutputClient by instance()

    val logString: Variable<String> = Variable("")
    @Volatile private var readBytes = 0L

    override fun onEnterScope(scope: MortarScope?) {
        super.onEnterScope(scope)
        when (buildStep.isFinished()) {
            true -> getActions()
            false -> buildStep.actions.firstOrNull()?.let { pollRunningAction(it) }
        }
    }

    fun getActions() {
        val actions = buildStep.actions.mapNotNull { it.outputUrl }
        Observable.concat(actions.map { outputClient.fetchOutput(it) })
            .subscribeOnIoObserveOnUI()
            .subscribeNext { appendLog(it) }
            .addTo(bag)
    }

    internal fun runningOutput(action: BuildAction): Observable<ByteArray> =
        polling(LOG_POLLING_SECONDS) { fetchRawOutput(action) }
            .takeUntil(stepFinished(action.step))
            .concatWith(Observable.defer { fetchRawOutput(action) })
            .filter { it.isNotEmpty() }
            .doOnNext { readBytes += it.size }

    private fun pollRunningAction(action: BuildAction) {
        runningOutput(action)
            .subscribeOnIoObserveOnUI()
            .subscribeNext { appendLog(String(it)) }
            .addTo(bag)
    }

    private fun fetchRawOutput(action: BuildAction): Observable<ByteArray> =
        outputClient.fetchRawOutput(build.rawOutputUrl(action), readBytes)

    private fun stepFinished(stepNumber: Int?): Observable<Build> =
        polling(BuildPresenter.BUILD_POLLING_SECONDS) {
            api.getBuild(build.vcsType(), build.username!!, build.reponame!!, build.buildNum!!)
        }.filter { it.isFinished() || it.steps?.findByStepNumber(stepNumber)?.isFinished() == true }

    private fun appendLog(log: String) {
        logString.value = logString.value + log.removeAnsiEscapeCode().replaceAnsiColorCodeToHtml()
    }

    companion object {
        const val LOG_POLLING_SECONDS = 1L
    }
}
