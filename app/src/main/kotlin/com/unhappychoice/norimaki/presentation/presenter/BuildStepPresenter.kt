package com.unhappychoice.norimaki.presentation.presenter

import com.github.unhappychoice.circleci.CircleCIAPIClientV1_1
import com.github.unhappychoice.circleci.v1.response.Build
import com.github.unhappychoice.circleci.v1.response.BuildStep
import com.unhappychoice.norimaki.domain.model.findByStepNumber
import com.unhappychoice.norimaki.domain.model.isFinished
import com.unhappychoice.norimaki.domain.model.rawOutputUrl
import com.unhappychoice.norimaki.domain.model.step
import com.unhappychoice.norimaki.domain.model.vcsType
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

class BuildStepPresenter internal constructor(
    private val createOutputClient: (String) -> BuildOutputClient
) : PresenterNeedsToken<BuildStepView>() {
    constructor() : this({ BuildOutputClient(it) })

    val build: Build by instance()
    val buildStep: BuildStep by instance()
    private lateinit var outputClient: BuildOutputClient

    val logString: Variable<String> = Variable("")

    val logLoadFailed = Variable(false)

    override fun onAuthenticated() {
        outputClient = createOutputClient(requireNotNull(account).token)
        when (buildStep.isFinished()) {
            true -> getActions()
            false -> pollRunningActions()
        }
    }

    fun getActions() {
        if (account == null) return
        val actions = buildStep.actions.mapNotNull { it.outputUrl }
        completedActionLogs(actions, outputClient::fetchOutput)
            .subscribeOnIoObserveOnUI()
            .subscribeNext { appendLog(it) }
            .addTo(bag)
    }

    internal fun runningOutput(client: CircleCIAPIClientV1_1, outputClient: BuildOutputClient): Observable<String> {
        val outputUrls = buildStep.actions.map { build.rawOutputUrl(it) }
        return pollActionOutputs(
            outputUrls,
            stepFinished(client, buildStep.step()),
            outputClient::fetchRawOutput,
            fetchFinalOutput = { url, offset -> outputClient.fetchFinalRawOutput(url, offset) }
        )
    }

    private fun pollRunningActions() {
        val client = account?.client ?: return
        runningOutput(client, outputClient)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread(), true)
            .subscribeBy(
                onNext = { appendLog(it) },
                onError = { logLoadFailed.value = true }
            )
            .addTo(bag)
    }

    private fun stepFinished(client: CircleCIAPIClientV1_1, stepNumber: Int?): Observable<Build> =
        polling(BuildPresenter.BUILD_POLLING_SECONDS) {
            client.getBuild(build.vcsType(), build.username!!, build.reponame!!, build.buildNum!!)
        }.filter { it.isFinished() || it.steps?.findByStepNumber(stepNumber)?.isFinished() == true }

    private fun appendLog(log: String) {
        logString.value = logString.value + log.removeAnsiEscapeCode().replaceAnsiColorCodeToHtml()
    }

    companion object {
        const val LOG_POLLING_SECONDS = 1L
    }
}

internal fun completedActionLogs(
    outputUrls: List<String>,
    fetchOutput: (String) -> Observable<String>
): Observable<String> =
    Observable.fromIterable(outputUrls).concatMap { url ->
        Observable.defer { fetchOutput(url) }
            .onErrorReturnItem("\n[Some output could not be loaded.]\n")
    }

internal fun pollActionOutputs(
    outputUrls: List<String>,
    stepFinished: Observable<Build>,
    fetchOutput: (String, Long) -> Observable<ByteArray>,
    scheduler: Scheduler = Schedulers.computation(),
    fetchFinalOutput: (String, Long) -> Observable<ByteArray> = fetchOutput
): Observable<String> = Observable.defer {
    val urls = outputUrls.distinct()
    val decoders = urls.associateWith { Utf8StreamDecoder() }
    val offsets = mutableMapOf<String, Long>()
    // Stop scheduling reads at completion, then drain after any in-flight reads.
    polling(BuildStepPresenter.LOG_POLLING_SECONDS, scheduler = scheduler) { Observable.just(false) }
        .takeUntil(stepFinished)
        .concatWith(Observable.just(true))
        .concatMap { finishing ->
            val reads = urls.map { url ->
                val read = Observable.defer {
                    val decoder = decoders.getValue(url)
                    val offset = offsets.getOrDefault(url, 0L)
                    val fetch = if (finishing) fetchFinalOutput else fetchOutput
                    val chunks = fetch(url, offset)
                        .filter { it.isNotEmpty() }
                        .doOnNext { offsets[url] = offsets.getOrDefault(url, 0L) + it.size }
                        .map { decoder.decode(it) }
                    if (finishing) chunks.concatWith(Observable.fromCallable { decoder.finish() }) else chunks
                }
                if (finishing) read else read.onErrorResumeNext(Observable.empty<String>())
            }
            Observable.concatDelayError(reads).filter { it.isNotEmpty() }
        }
}
