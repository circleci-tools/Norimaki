package com.unhappychoice.norimaki.presentation.presenter

import com.github.unhappychoice.circleci.v1.response.Build
import com.github.unhappychoice.circleci.v1.response.BuildAction
import com.github.unhappychoice.circleci.v1.response.BuildStep
import com.unhappychoice.norimaki.domain.model.BuildId
import com.unhappychoice.norimaki.domain.model.BuildStepId
import com.unhappychoice.norimaki.domain.model.isFinished
import com.unhappychoice.norimaki.domain.model.rawOutputUrl
import com.unhappychoice.norimaki.extension.*
import com.unhappychoice.norimaki.infrastructure.output.BuildOutputClient
import com.unhappychoice.norimaki.presentation.presenter.core.PresenterNeedsToken
import com.unhappychoice.norimaki.presentation.view.BuildStepView
import io.reactivex.Observable
import io.reactivex.rxkotlin.addTo
import org.kodein.di.instance

class BuildStepPresenter: PresenterNeedsToken<BuildStepView>() {
    private val buildId: BuildId by instance()
    private val stepId: BuildStepId by instance()
    private val outputClient: BuildOutputClient by instance()

    val logString: Variable<String> = Variable("")
    @Volatile private var readBytes = 0L

    fun loadStep(onLoaded: (BuildStep) -> Unit) {
        readBytes = 0L
        logString.value = ""
        polling(BuildPresenter.BUILD_POLLING_SECONDS) { buildId.load(api) }
            .take(1)
            .subscribeOnIoObserveOnUI()
            .subscribeNext { build ->
                val step = stepId.resolve(build.steps) ?: return@subscribeNext
                onLoaded(step)
                if (step.isFinished()) getActions(step)
                else step.actions.firstOrNull()?.let { pollRunningAction(build, it) }
            }
            .addTo(bag)
    }

    private fun getActions(buildStep: BuildStep) {
        val actions = buildStep.actions.mapNotNull { it.outputUrl }
        Observable.concat(actions.map { outputClient.fetchOutput(it) })
            .subscribeOnIoObserveOnUI()
            .subscribeNext { appendLog(it) }
            .addTo(bag)
    }

    private fun pollRunningAction(build: Build, action: BuildAction) {
        polling(LOG_POLLING_SECONDS) { fetchRawOutput(build, action) }
            .takeUntil(stepFinished())
            .concatWith(Observable.defer { fetchRawOutput(build, action) })
            .filter { it.isNotEmpty() }
            .doOnNext { readBytes += it.size }
            .subscribeOnIoObserveOnUI()
            .subscribeNext { appendLog(String(it)) }
            .addTo(bag)
    }

    private fun fetchRawOutput(build: Build, action: BuildAction): Observable<ByteArray> =
        outputClient.fetchRawOutput(build.rawOutputUrl(action), readBytes)

    private fun stepFinished(): Observable<Build> =
        polling(BuildPresenter.BUILD_POLLING_SECONDS) {
            buildId.load(api)
        }.filter { it.isFinished() || stepId.resolve(it.steps)?.isFinished() == true }

    private fun appendLog(log: String) {
        logString.value = logString.value + log.removeAnsiEscapeCode().replaceAnsiColorCodeToHtml()
    }

    companion object {
        const val LOG_POLLING_SECONDS = 1L
    }
}
