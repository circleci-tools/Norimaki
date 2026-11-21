package com.unhappychoice.norimaki.presentation.presenter

import com.github.unhappychoice.circleci.CircleCIAPIClientV1_1
import com.github.unhappychoice.circleci.v1.response.Build
import com.github.unhappychoice.circleci.v1.response.BuildStep
import com.unhappychoice.norimaki.domain.model.BuildId
import com.unhappychoice.norimaki.domain.model.BuildStepId
import com.unhappychoice.norimaki.domain.model.isFinished
import com.unhappychoice.norimaki.domain.model.rawOutputUrl
import com.unhappychoice.norimaki.extension.Utf8StreamDecoder
import com.unhappychoice.norimaki.extension.Variable
import com.unhappychoice.norimaki.extension.polling
import com.unhappychoice.norimaki.extension.subscribeOnIoObserveOnUI
import com.unhappychoice.norimaki.infrastructure.output.BuildOutputClient
import com.unhappychoice.norimaki.infrastructure.output.OUTPUT_LIMIT_MESSAGE
import com.unhappychoice.norimaki.infrastructure.output.RawOutputChunk
import com.unhappychoice.norimaki.presentation.log.BuildLog
import com.unhappychoice.norimaki.presentation.presenter.core.PresenterNeedsToken
import com.unhappychoice.norimaki.presentation.view.BuildStepView
import io.reactivex.Observable
import io.reactivex.Scheduler
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.addTo
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

    internal val log = BuildLog()

    val logLoadFailed = Variable(false)

    val canRetryStepLoad = Variable(false)

    fun retryStepLoad() {
        if (canRetryStepLoad.value) loadStep()
    }

    override fun onAuthenticated() = loadStep()

    private fun loadStep() {
        val verified = account ?: return
        val client = verified.client
        val outputClient = createOutputClient(verified.token)
        log.clear()
        logLoadFailed.value = false
        canRetryStepLoad.value = false
        pollBuildStep(stepId) { buildId.load(client) }
            .subscribeOnIoObserveOnUI()
            .subscribeBy(
                onNext = { (build, step) ->
                    view?.showStep(step)
                    if (step.isFinished()) getActions(step, outputClient)
                    else pollRunningActions(build, step, client, outputClient)
                },
                onError = {
                    canRetryStepLoad.value = true
                    logLoadFailed.value = true
                }
            )
            .addTo(bag)
    }

    private fun getActions(buildStep: BuildStep, outputClient: BuildOutputClient) {
        val actions = buildStep.actions.mapNotNull { it.outputUrl }
        completedActionLogs(actions, outputClient::fetchOutput).renderLog()
    }

    internal fun runningOutput(
        build: Build,
        buildStep: BuildStep,
        client: CircleCIAPIClientV1_1,
        outputClient: BuildOutputClient
    ): Observable<String> {
        val outputUrls = buildStep.actions.map { build.rawOutputUrl(it) }
        return pollActionOutputs(
            outputUrls,
            actionSnapshots(client),
            outputClient::fetchRawOutput,
            fetchFinalOutput = { url, offset -> outputClient.fetchFinalRawOutput(url, offset) }
        )
    }

    private fun pollRunningActions(
        build: Build,
        buildStep: BuildStep,
        client: CircleCIAPIClientV1_1,
        outputClient: BuildOutputClient
    ) {
        runningOutput(build, buildStep, client, outputClient).renderLog()
    }

    private fun actionSnapshots(client: CircleCIAPIClientV1_1): Observable<ActionSnapshot> =
        polling(BuildPresenter.BUILD_POLLING_SECONDS) {
            buildId.load(client)
        }.map { build ->
            val step = stepId.resolve(build.steps)
            ActionSnapshot(
                step?.actions.orEmpty().map { build.rawOutputUrl(it) },
                build.isFinished() || step?.isFinished() == true
            )
        }

    private fun Observable<String>.renderLog() {
        val chunks = subscribeOn(Schedulers.io()).observeOn(Schedulers.computation(), true)
        log.render(chunks, AndroidSchedulers.mainThread(), onError = { logLoadFailed.value = true })
            .addTo(bag)
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
    Observable.defer(fetchBuild)
        .concatWith(
            Observable.timer(BuildPresenter.BUILD_POLLING_SECONDS, TimeUnit.SECONDS, scheduler)
                .flatMap { polling(BuildPresenter.BUILD_POLLING_SECONDS, scheduler = scheduler, fetch = fetchBuild) }
        )
        .flatMap { build ->
            val step = stepId.resolve(build.steps)
            when {
                step != null -> Observable.just(build to step)
                build.isFinished() -> Observable.error(NoSuchElementException("Build step was not found"))
                else -> Observable.empty()
            }
        }
        .take(1)

internal fun completedActionLogs(
    outputUrls: List<String>,
    fetchOutput: (String) -> Observable<String>
): Observable<String> =
    Observable.fromIterable(outputUrls).concatMap { url ->
        Observable.defer { fetchOutput(url) }
            .onErrorReturnItem("\n[Some output could not be loaded.]\n")
    }

internal data class ActionSnapshot(val outputUrls: List<String>, val finished: Boolean)

internal fun pollActionOutputs(
    outputUrls: List<String>,
    snapshots: Observable<ActionSnapshot>,
    fetchOutput: (String, Long) -> Observable<RawOutputChunk>,
    scheduler: Scheduler = Schedulers.computation(),
    fetchFinalOutput: (String, Long) -> Observable<RawOutputChunk> = fetchOutput
): Observable<String> = Observable.defer {
    val urls = AtomicReference(outputUrls.toSet())
    val decoders = mutableMapOf<String, Utf8StreamDecoder>()
    val limitedUrls = mutableSetOf<String>()
    val offsets = mutableMapOf<String, Long>()
    val reading = AtomicBoolean()
    val stepFinished = snapshots
        .doOnNext { snapshot -> urls.set(urls.get() + snapshot.outputUrls) }
        .filter { it.finished }
    polling(BuildStepPresenter.LOG_POLLING_SECONDS, scheduler = scheduler) { Observable.just(false) }
        .filter { reading.compareAndSet(false, true) }
        .takeUntil(stepFinished)
        .concatWith(Observable.just(true))
        .concatMap { finishing ->
            val reads = urls.get().filterNot(limitedUrls::contains).map { url ->
                val read = Observable.defer {
                    val decoder = decoders.getOrPut(url) { Utf8StreamDecoder() }
                    val offset = offsets.getOrDefault(url, 0L)
                    val fetch = if (finishing) fetchFinalOutput else fetchOutput
                    val chunks = fetch(url, offset).map { chunk ->
                        offsets[url] = offsets.getOrDefault(url, 0L) + chunk.bytes.size
                        val log = decoder.decode(chunk.bytes)
                        if (chunk.limitReached) {
                            limitedUrls.add(url)
                            log + decoder.finish() + OUTPUT_LIMIT_MESSAGE
                        } else {
                            log
                        }
                    }
                    if (finishing) chunks.concatWith(Observable.fromCallable {
                        if (url in limitedUrls) "" else decoder.finish()
                    }) else chunks
                }
                if (finishing) read else read.onErrorResumeNext(Observable.empty<String>())
            }
            val batch = Observable.concatDelayError(reads).filter { it.isNotEmpty() }
            if (finishing) batch else batch.doOnComplete { reading.set(false) }
        }
}
