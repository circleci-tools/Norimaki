package com.unhappychoice.norimaki.presentation.presenter

import com.github.unhappychoice.circleci.v1.response.Build
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
import io.reactivex.Scheduler
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import org.kodein.di.instance

class BuildStepPresenter : PresenterNeedsToken<BuildStepView>() {
    private val buildId: BuildId by instance()
    private val stepId: BuildStepId by instance()
    private val outputClient: BuildOutputClient by instance()

    val logString: Variable<String> = Variable("")

    val logLoadFailed = Variable(false)

    fun loadStep(onLoaded: (BuildStep) -> Unit) {
        logString.value = ""
        logLoadFailed.value = false
        pollBuildStep(stepId) { buildId.load(api) }
            .subscribeOnIoObserveOnUI()
            .subscribeBy(
                onNext = { (build, step) ->
                    onLoaded(step)
                    if (step.isFinished()) getActions(step)
                    else pollRunningActions(build, step)
                },
                onError = { logLoadFailed.value = true }
            )
            .addTo(bag)
    }

    private fun getActions(buildStep: BuildStep) {
        val actions = buildStep.actions.mapNotNull { it.outputUrl }
        Observable.concat(actions.map { outputClient.fetchOutput(it) })
            .subscribeOnIoObserveOnUI()
            .subscribeBy(onNext = { appendLog(it) }, onError = { logLoadFailed.value = true })
            .addTo(bag)
    }

    private fun pollRunningActions(build: Build, buildStep: BuildStep) {
        val outputUrls = buildStep.actions.map { build.rawOutputUrl(it) }
        pollActionOutputs(outputUrls, stepFinished(), outputClient::fetchRawOutput, fetchFinalOutput = { url, offset ->
            outputClient.fetchFinalRawOutput(url, offset)
        })
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread(), true)
            .subscribeBy(
                onNext = { appendLog(String(it)) },
                onError = { logLoadFailed.value = true }
            )
            .addTo(bag)
    }

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

internal fun pollBuildStep(
    stepId: BuildStepId,
    scheduler: Scheduler = Schedulers.computation(),
    fetchBuild: () -> Observable<Build>
): Observable<Pair<Build, BuildStep>> =
    polling(BuildPresenter.BUILD_POLLING_SECONDS, scheduler = scheduler, fetch = fetchBuild)
        .flatMap { build ->
            val step = stepId.resolve(build.steps)
            when {
                step != null -> Observable.just(build to step)
                build.isFinished() -> Observable.error(NoSuchElementException("Build step was not found"))
                else -> Observable.empty()
            }
        }
        .take(1)

internal fun pollActionOutputs(
    outputUrls: List<String>,
    stepFinished: Observable<Build>,
    fetchOutput: (String, Long) -> Observable<ByteArray>,
    scheduler: Scheduler = Schedulers.computation(),
    fetchFinalOutput: (String, Long) -> Observable<ByteArray> = fetchOutput
): Observable<ByteArray> = Observable.defer {
    val offsets = mutableMapOf<String, Long>()
    // Stop scheduling reads at completion, then drain after any in-flight reads.
    polling(BuildStepPresenter.LOG_POLLING_SECONDS, scheduler = scheduler) { Observable.just(false) }
        .takeUntil(stepFinished)
        .concatWith(Observable.just(true))
        .concatMap { finishing ->
            val reads = outputUrls.map { url ->
                val read = Observable.defer {
                    val offset = offsets.getOrDefault(url, 0L)
                    val fetch = if (finishing) fetchFinalOutput else fetchOutput
                    fetch(url, offset)
                        .filter { it.isNotEmpty() }
                        .doOnNext { offsets[url] = offset + it.size }
                }
                if (finishing) read else read.onErrorResumeNext(Observable.empty<ByteArray>())
            }
            Observable.concatDelayError(reads)
        }
}
