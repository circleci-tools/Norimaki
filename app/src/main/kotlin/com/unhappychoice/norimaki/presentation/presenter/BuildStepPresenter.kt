package com.unhappychoice.norimaki.presentation.presenter

import com.github.unhappychoice.circleci.CircleCIAPIClientV1_1
import com.github.unhappychoice.circleci.v1.response.Build
import com.github.unhappychoice.circleci.v1.response.BuildStep
import com.unhappychoice.norimaki.domain.model.BuildId
import com.unhappychoice.norimaki.domain.model.BuildStepId
import com.unhappychoice.norimaki.domain.model.isFinished
import com.unhappychoice.norimaki.domain.model.rawOutputUrl
import com.unhappychoice.norimaki.extension.*
import com.unhappychoice.norimaki.infrastructure.output.BuildOutputClient
import com.unhappychoice.norimaki.infrastructure.output.OUTPUT_LIMIT_MESSAGE
import com.unhappychoice.norimaki.infrastructure.output.RawOutputChunk
import com.unhappychoice.norimaki.presentation.presenter.core.PresenterNeedsToken
import com.unhappychoice.norimaki.presentation.presenter.core.StartedPolling
import com.unhappychoice.norimaki.presentation.view.BuildStepView
import io.reactivex.Observable
import io.reactivex.Scheduler
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import org.kodein.di.instance
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class BuildStepPresenter internal constructor(
    private val createOutputClient: (String) -> BuildOutputClient
) : PresenterNeedsToken<BuildStepView>() {
    constructor() : this({ BuildOutputClient(it) })

    private val buildId: BuildId by instance()
    private val stepId: BuildStepId by instance()

    val logString: Variable<String> = Variable("")

    val logLoadFailed = Variable(false)

    val logSearchAvailable = Variable(false)

    val canRetryStepLoad = Variable(false)

    private var stepPolling: StartedPolling? = null

    fun retryStepLoad() {
        if (canRetryStepLoad.value) stepPolling?.refresh()
    }

    override fun onAuthenticated() {
        val verified = account ?: return
        val output = StepOutputProgress()
        val outputClient = createOutputClient(verified.token)
        logSearchAvailable.value = false
        logString.value = ""
        stepPolling = StartedPolling { loadStep(verified.client, outputClient, output) }
            .also { it.observe(activity.lifecycle, bag) }
    }

    private fun loadStep(
        client: CircleCIAPIClientV1_1,
        outputClient: BuildOutputClient,
        progress: StepOutputProgress
    ): Disposable {
        logLoadFailed.value = false
        canRetryStepLoad.value = false
        if (!progress.complete) logSearchAvailable.value = false
        return pollBuildStep(stepId, previousStep = progress.step) { buildId.load(client) }
            .subscribeOnIoObserveOnUI()
            .concatMap { (build, step) ->
                view?.showStep(step)
                progress.step = step
                when {
                    progress.complete -> Observable.empty()
                    !progress.raw.started && step.isFinished() -> completedOutput(step, outputClient, progress)
                    else -> runningOutput(build, step, client, outputClient, progress.raw, AndroidSchedulers.mainThread())
                }.doOnComplete { progress.complete = true }
            }
            .subscribeBy(
                onNext = ::appendLog,
                onComplete = ::allowLogSearch,
                onError = {
                    canRetryStepLoad.value = true
                    logLoadFailed.value = true
                    allowLogSearch()
                }
            )
    }

    private fun completedOutput(
        step: BuildStep,
        outputClient: BuildOutputClient,
        progress: StepOutputProgress
    ): Observable<String> =
        completedActionLogs(
            step.actions.mapNotNull { it.outputUrl }.drop(progress.completedActions),
            onActionOutput = { progress.completedActions++ }
        ) { url ->
            Observable.defer { outputClient.fetchOutput(url) }.subscribeOnIoObserveOnUI()
        }

    internal fun runningOutput(
        build: Build,
        buildStep: BuildStep,
        client: CircleCIAPIClientV1_1,
        outputClient: BuildOutputClient,
        progress: RawOutputProgress? = null,
        delivery: Scheduler = Schedulers.trampoline()
    ): Observable<String> {
        val actions = build.actionCompletion(buildStep)
        val snapshots = if (build.isFinished() || buildStep.isFinished()) {
            Observable.just(ActionSnapshot(actions, finished = true))
        } else {
            actionSnapshots(client).observeOn(delivery)
        }
        // Deliver chunks on the same scheduler that commits their offsets and appends the log.
        return pollActionOutputs(
            actions,
            snapshots,
            { url, offset -> outputClient.fetchRawOutput(url, offset).observeOn(delivery, true) },
            progress = progress,
            delivery = delivery,
            fetchFinalOutput = { url, offset -> outputClient.fetchFinalRawOutput(url, offset).observeOn(delivery, true) }
        )
    }

    private fun actionSnapshots(client: CircleCIAPIClientV1_1): Observable<ActionSnapshot> =
        polling(BuildPresenter.BUILD_POLLING_SECONDS) {
            buildId.load(client)
        }.delaySubscription(BuildPresenter.BUILD_POLLING_SECONDS, TimeUnit.SECONDS).map { build ->
            val step = stepId.resolve(build.steps)
            ActionSnapshot(
                build.actionCompletion(step),
                build.isFinished() || step?.isFinished() == true
            )
        }

    private fun allowLogSearch() {
        logSearchAvailable.value = logString.value.isNotEmpty()
    }

    private fun appendLog(log: String) {
        logString.value = logString.value + log.replaceAnsiColorCodeToHtml()
    }

    companion object {
        const val LOG_POLLING_SECONDS = 1L
    }
}

internal fun pollBuildStep(
    stepId: BuildStepId,
    scheduler: Scheduler = Schedulers.computation(),
    previousStep: BuildStep? = null,
    fetchBuild: () -> Observable<Build>
): Observable<Pair<Build, BuildStep>> =
    Observable.defer(fetchBuild)
        .concatWith(
            Observable.timer(BuildPresenter.BUILD_POLLING_SECONDS, TimeUnit.SECONDS, scheduler)
                .flatMap { polling(BuildPresenter.BUILD_POLLING_SECONDS, scheduler = scheduler, fetch = fetchBuild) }
        )
        .flatMap { build ->
            val step = stepId.resolve(build.steps)
            when {
                step != null -> Observable.just(build to step)
                build.isFinished() && previousStep != null -> Observable.just(build to previousStep)
                build.isFinished() -> Observable.error(NoSuchElementException("Build step was not found"))
                else -> Observable.empty()
            }
        }
        .take(1)

internal fun completedActionLogs(
    outputUrls: List<String>,
    onActionOutput: () -> Unit = {},
    fetchOutput: (String) -> Observable<String>
): Observable<String> =
    Observable.fromIterable(outputUrls).concatMap { url ->
        val ansiStream = AnsiEscapeStream()
        Observable.defer { fetchOutput(url) }
            .map(ansiStream::strip)
            .onErrorReturnItem("\n[Some output could not be loaded.]\n")
            .doOnNext { onActionOutput() }
            .filter { it.isNotEmpty() }
    }

private fun Build.actionCompletion(step: BuildStep?): Map<String, Boolean> =
    step?.actions.orEmpty().associate { rawOutputUrl(it) to (it.endTime != null) }

internal data class ActionSnapshot(val actionCompletion: Map<String, Boolean>, val finished: Boolean)

private class StepOutputProgress {
    var step: BuildStep? = null
    var complete = false
    var completedActions = 0
    val raw = RawOutputProgress()
}

internal class RawOutputProgress {
    val actions = AtomicReference(emptyMap<String, Boolean>())
    val caughtUpUrls = mutableSetOf<String>()
    val decoders = mutableMapOf<String, Utf8StreamDecoder>()
    val ansiStreams = mutableMapOf<String, AnsiEscapeStream>()
    val finishedUrls = mutableSetOf<String>()
    val offsets = mutableMapOf<String, Long>()
    val started get() = actions.get().isNotEmpty()

    fun rememberActions(completion: Map<String, Boolean>) {
        val known = actions.get()
        actions.set(known + completion.mapValues { (url, finished) -> finished || known[url] == true })
    }
}

internal fun pollActionOutputs(
    initialActionCompletion: Map<String, Boolean>,
    snapshots: Observable<ActionSnapshot>,
    fetchOutput: (String, Long) -> Observable<RawOutputChunk>,
    scheduler: Scheduler = Schedulers.computation(),
    progress: RawOutputProgress? = null,
    delivery: Scheduler = Schedulers.trampoline(),
    fetchFinalOutput: (String, Long) -> Observable<RawOutputChunk> = fetchOutput
): Observable<String> = Observable.defer {
    val state = progress ?: RawOutputProgress()
    state.rememberActions(initialActionCompletion)
    val decoders = state.decoders
    val ansiStreams = state.ansiStreams
    val finishedUrls = state.finishedUrls
    val offsets = state.offsets
    val reading = AtomicBoolean()
    val stepFinished = snapshots
        .doOnNext { snapshot -> state.rememberActions(snapshot.actionCompletion) }
        .filter { it.finished }
    polling(BuildStepPresenter.LOG_POLLING_SECONDS, scheduler = scheduler) { Observable.just(false).observeOn(delivery) }
        .filter { reading.compareAndSet(false, true) }
        .takeUntil(stepFinished)
        .concatWith(Observable.just(true))
        .concatMap { finishing ->
            val reads = state.actions.get().filterKeys { url ->
                url !in finishedUrls && (finishing || url !in state.caughtUpUrls)
            }.map { (url, finished) ->
                val read = Observable.defer {
                    val decoder = decoders.getOrPut(url) { Utf8StreamDecoder() }
                    val offset = offsets.getOrDefault(url, 0L)
                    val fetch = if (finishing || finished) fetchFinalOutput else fetchOutput
                    val ansiStream = ansiStreams.getOrPut(url) { AnsiEscapeStream() }
                    val chunks = fetch(url, offset).map { chunk ->
                        offsets[url] = offsets.getOrDefault(url, 0L) + chunk.bytes.size
                        val log = decoder.decode(chunk.bytes)
                        if (chunk.limitReached) {
                            finishedUrls.add(url)
                            ansiStream.strip(log + decoder.finish()) + OUTPUT_LIMIT_MESSAGE
                        } else {
                            ansiStream.strip(log)
                        }
                    }
                    when {
                        finishing -> chunks.concatWith(Observable.fromCallable {
                            if (finishedUrls.add(url)) ansiStream.strip(decoder.finish()) else ""
                        })
                        finished -> chunks.doOnComplete { state.caughtUpUrls.add(url) }
                        else -> chunks
                    }
                }
                if (finishing) read else read.onErrorResumeNext(Observable.empty<String>())
            }
            val batch = Observable.concatDelayError(reads).filter { it.isNotEmpty() }
            if (finishing) batch else batch.doOnComplete { reading.set(false) }
        }
}
