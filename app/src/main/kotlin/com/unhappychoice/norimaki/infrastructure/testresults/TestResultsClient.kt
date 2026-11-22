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
    private val client: OkHttpClient = OkHttpClient()
) {
    fun fetch(buildId: BuildId): Observable<TestResults> = Observable.create<TestResults> { emitter ->
        try {
            val url = buildId.testResultsUrl()
            if (url == null) emitter.onNext(TestResults.Unavailable)
            else fetchPages(url, emitter)
            emitter.onComplete()
        } catch (error: Exception) {
            emitter.tryOnError(error)
        }
    }.subscribeOn(Schedulers.io())

    private fun fetchPages(url: HttpUrl, emitter: ObservableEmitter<TestResults>) {
        val seenTokens = mutableSetOf<String>()
        var hasResults = false
        var pageToken: String? = null
        do {
            if (emitter.isDisposed) return
            val page = fetchPage(url, pageToken, emitter)
            if (page == null) {
                emitter.onNext(TestResults.Unavailable)
                return
            }
            val items = page.getAsJsonArray("items")
            hasResults = hasResults || items.size() > 0
            val failures = items.mapNotNull { it.asJsonObject.toFailure() }
            if (failures.isNotEmpty()) emitter.onNext(TestResults.FailurePage(failures))
            pageToken = page.stringOrNull("next_page_token")?.takeIf { it.isNotEmpty() }
            if (pageToken != null && !seenTokens.add(pageToken)) {
                throw IOException("Repeated test results page token")
            }
        } while (pageToken != null)
        emitter.onNext(if (hasResults) TestResults.Reported else TestResults.Unavailable)
    }

    private fun fetchPage(url: HttpUrl, pageToken: String?, emitter: ObservableEmitter<TestResults>): JsonObject? {
        val pageUrl = if (pageToken == null) url else url.newBuilder().addQueryParameter("page-token", pageToken).build()
        val request = Request.Builder().url(pageUrl).header("Circle-Token", token).build()
        val call = client.newCall(request)
        emitter.setCancellable(call::cancel)
        return call.execute().use { response ->
            when {
                response.code == 404 && pageToken == null -> null
                response.isSuccessful -> JsonParser.parseString(response.body.string()).asJsonObject
                else -> throw IOException("Test results request failed with HTTP ${response.code}")
            }
        }
    }
}

internal sealed interface TestResults {
    data object Unavailable : TestResults
    data object Reported : TestResults
    data class FailurePage(val failures: List<TestFailure>) : TestResults
}

internal data class TestFailure(val name: String, val className: String?, val message: String?)

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
