package com.unhappychoice.norimaki.infrastructure.flakytests

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.unhappychoice.norimaki.domain.model.ProjectId
import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

internal class FlakyTestsClient(
    private val token: String,
    client: OkHttpClient = OkHttpClient()
) {
    private val client = client.newBuilder().followRedirects(false).build()

    fun fetch(projectId: ProjectId): Observable<List<FlakyTestSummary>> = Observable.fromCallable {
        val request = Request.Builder().url(projectId.flakyTestsUrl()).header("Circle-Token", token).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Flaky tests request failed with HTTP ${response.code}")
            val body = JsonParser.parseString(response.body.string()).asJsonObject
            val tests = body.getAsJsonArray("flaky-tests") ?: JsonArray()
            tests.map { it.asJsonObject }
                .groupBy { it.get("test-name").asString to it.stringOrNull("classname") }
                .map { (identity, rows) ->
                    val occurrences = rows.sumOf { it.get("times-flaked").asInt }
                    FlakyTestSummary(identity.first, identity.second, occurrences)
                }
                .sortedByDescending { it.occurrences }
        }
    }.subscribeOn(Schedulers.io())
}

internal data class FlakyTestSummary(val testName: String, val className: String?, val occurrences: Int)

private fun ProjectId.flakyTestsUrl() =
    "https://circleci.com/api/v2/insights/".toHttpUrl().newBuilder()
        .addPathSegment(if (vcsType == "bitbucket") "bb" else "gh")
        .addPathSegment(owner)
        .addPathSegment(repository)
        .addPathSegment("flaky-tests")
        .build()

private fun JsonObject.stringOrNull(key: String): String? = get(key)?.takeUnless { it.isJsonNull }?.asString
