package com.unhappychoice.norimaki.infrastructure.testresults

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.unhappychoice.norimaki.domain.model.BuildId
import io.reactivex.Observable
import io.reactivex.ObservableEmitter
import io.reactivex.schedulers.Schedulers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

internal class TestResultsClient(
    private val token: String,
    client: OkHttpClient = OkHttpClient(),
    private val maxResponseBytes: Long = 1024L * 1024,
    private val maxFailureCost: Int = 512 * 1024
) {
    private val client = client.newBuilder().followRedirects(false).build()

    fun fetch(
        buildId: BuildId,
        resumeFrom: TestResultsCursor? = null
    ): Observable<TestResults> = Observable.create<TestResults> { emitter ->
        try {
            val url = buildId.testResultsUrl()
            if (url == null) emitter.onNext(TestResults.Unavailable)
            else fetchPages(url, resumeFrom, emitter)
            emitter.onComplete()
        } catch (_: ResponseLimitReached) {
            emitter.onNext(TestResults.LimitReached)
            emitter.onComplete()
        } catch (error: Exception) {
            emitter.tryOnError(error)
        }
    }.subscribeOn(Schedulers.io())

    private fun fetchPages(url: HttpUrl, resumeFrom: TestResultsCursor?, emitter: ObservableEmitter<TestResults>) {
        val seenTokens = generateSequence(resumeFrom) { it.previous }.map { it.token }.toMutableSet()
        var remainingFailureCost = maxFailureCost - (resumeFrom?.failureCost ?: 0)
        var cursor = resumeFrom
        var hasResults = resumeFrom?.hasResults ?: false
        do {
            if (emitter.isDisposed) return
            val page = fetchPage(url, cursor?.token, emitter)
            if (page == null) {
                emitter.onNext(TestResults.Unavailable)
                return
            }
            val items = page.getAsJsonArray("items")
            val failures = mutableListOf<TestFailure>()
            for (item in items) {
                val failure = item.asJsonObject.toFailure() ?: continue
                if (failure.cost > remainingFailureCost) {
                    emitLimitReached(failures, emitter)
                    return
                }
                remainingFailureCost -= failure.cost
                failures.add(failure)
            }
            hasResults = hasResults || items.size() > 0
            val nextToken = page.stringOrNull("next_page_token")?.takeIf { it.isNotEmpty() }
            if (nextToken != null && !seenTokens.add(nextToken)) {
                throw IOException("Repeated test results page token")
            }
            if (nextToken != null && (seenTokens.size >= MAX_PAGES || nextToken.length > MAX_TOKEN_LENGTH)) {
                emitLimitReached(failures, emitter)
                return
            }
            cursor = nextToken?.let {
                TestResultsCursor(it, hasResults, cursor, maxFailureCost - remainingFailureCost)
            }
            if (failures.isNotEmpty() || cursor != null) emitter.onNext(TestResults.FailurePage(failures, cursor))
        } while (cursor != null)
        emitter.onNext(if (hasResults) TestResults.Reported else TestResults.Unavailable)
    }

    private fun emitLimitReached(failures: List<TestFailure>, emitter: ObservableEmitter<TestResults>) {
        if (failures.isNotEmpty()) emitter.onNext(TestResults.FailurePage(failures))
        emitter.onNext(TestResults.LimitReached)
    }

    private fun fetchPage(url: HttpUrl, pageToken: String?, emitter: ObservableEmitter<TestResults>): JsonObject? {
        val pageUrl = if (pageToken == null) url else url.newBuilder().addQueryParameter("page-token", pageToken).build()
        val request = Request.Builder().url(pageUrl).header("Circle-Token", token).build()
        val call = client.newCall(request)
        emitter.setCancellable(call::cancel)
        return call.execute().use { response ->
            when {
                response.code == 404 && pageToken == null -> null
                response.isSuccessful -> {
                    val body = response.body
                    if (body.source().request(maxResponseBytes + 1)) throw ResponseLimitReached()
                    JsonParser.parseString(body.string()).asJsonObject
                }
                else -> throw IOException("Test results request failed with HTTP ${response.code}")
            }
        }
    }
}

internal sealed interface TestResults {
    data object Unavailable : TestResults
    data object Reported : TestResults
    data object LimitReached : TestResults
    data class FailurePage(val failures: List<TestFailure>, val nextCursor: TestResultsCursor? = null) : TestResults
}

internal data class TestResultsCursor(
    val token: String,
    val hasResults: Boolean,
    val previous: TestResultsCursor?,
    val failureCost: Int = 0
)

internal data class TestFailure(val name: String, val className: String?, val message: String?)

private const val MAX_PAGES = 100
private const val MAX_TOKEN_LENGTH = 4096
private const val FAILURE_ROW_COST = 128

private class ResponseLimitReached : IOException()

private val TestFailure.cost: Int
    get() = FAILURE_ROW_COST + name.length + (className?.length ?: 0) + (message?.length ?: 0)

private fun BuildId.testResultsUrl(): HttpUrl? {
    if (owner.isBlank() || repository.isBlank()) return null
    val vcs = if (vcsType == "bitbucket") "bb" else "gh"
    return "https://circleci.com/api/v2/project/".toHttpUrl().newBuilder()
        .addPathSegment(vcs)
        .addPathSegment(owner)
        .addPathSegment(repository)
        .addPathSegment(number.toString())
        .addPathSegment("tests")
        .build()
}

private fun JsonObject.toFailure(): TestFailure? = when (get("result").asString) {
    "failure", "error" -> TestFailure(get("name").asString, stringOrNull("classname"), stringOrNull("message"))
    else -> null
}

private fun JsonObject.stringOrNull(key: String): String? = get(key)?.takeUnless { it.isJsonNull }?.asString
