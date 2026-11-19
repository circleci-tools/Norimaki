package com.unhappychoice.norimaki.presentation.presenter

import com.github.unhappychoice.circleci.CircleCIAPIClientV1_1
import com.github.unhappychoice.circleci.v1.response.Build
import com.github.unhappychoice.circleci.v1.response.BuildStep
import com.unhappychoice.norimaki.domain.model.findByStepNumber
import com.unhappychoice.norimaki.domain.model.isFinished
import com.unhappychoice.norimaki.domain.model.rawOutputUrl
import com.unhappychoice.norimaki.domain.model.step
import com.unhappychoice.norimaki.domain.model.vcsType
import com.unhappychoice.norimaki.extension.Utf8StreamDecoder
import com.unhappychoice.norimaki.extension.Variable
import com.unhappychoice.norimaki.extension.polling
import com.unhappychoice.norimaki.infrastructure.output.BuildOutputClient
import com.unhappychoice.norimaki.presentation.log.BuildLog
import com.unhappychoice.norimaki.presentation.presenter.core.PresenterNeedsToken
import com.unhappychoice.norimaki.presentation.view.BuildStepView
import io.reactivex.Observable
import io.reactivex.Scheduler
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.addTo
import io.reactivex.schedulers.Schedulers
import org.kodein.di.instance
import java.util.concurrent.atomic.AtomicBoolean

class BuildStepPresenter internal constructor(
    private val createOutputClient: (String) -> BuildOutputClient
) : PresenterNeedsToken<BuildStepView>() {
    constructor() : this({ token -> BuildOutputClient(token) })

    val build: Build by instance()
    val buildStep: BuildStep by instance()
    private lateinit var outputClient: BuildOutputClient

    internal val log = BuildLog()
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
        completedActionLogs(actions, outputClient::fetchOutput).renderLog()
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
        runningOutput(client, outputClient).renderLog()
    }

    private fun stepFinished(client: CircleCIAPIClientV1_1, stepNumber: Int?): Observable<Build> =
        polling(BuildPresenter.BUILD_POLLING_SECONDS) {
            client.getBuild(build.vcsType(), build.username!!, build.reponame!!, build.buildNum!!)
        }.filter { it.isFinished() || it.steps?.findByStepNumber(stepNumber)?.isFinished() == true }

    private fun Observable<String>.renderLog() {
        val chunks = subscribeOn(Schedulers.io()).observeOn(Schedulers.computation(), true)
        log.render(chunks, AndroidSchedulers.mainThread(), onError = { logLoadFailed.value = true })
            .addTo(bag)
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
    val reading = AtomicBoolean()
    polling(BuildStepPresenter.LOG_POLLING_SECONDS, scheduler = scheduler) { Observable.just(false) }
        .filter { reading.compareAndSet(false, true) }
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
            val batch = Observable.concatDelayError(reads).filter { it.isNotEmpty() }
            if (finishing) batch else batch.doOnComplete { reading.set(false) }
        }
}
