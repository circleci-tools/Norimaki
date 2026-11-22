package com.unhappychoice.norimaki.infrastructure.workflow

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.unhappychoice.norimaki.domain.model.WorkflowJob
import io.reactivex.Completable
import io.reactivex.Single
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class WorkflowClient(
    private val token: String,
    private val client: OkHttpClient = OkHttpClient.Builder().followRedirects(false).retryOnConnectionFailure(false).build(),
    private val gson: Gson = Gson()
) {
    fun jobs(workflowId: String): Single<List<WorkflowJob>> = Single.fromCallable {
        val jobs = mutableListOf<WorkflowJob>()
        val seenTokens = mutableSetOf<String>()
        var pageToken: String? = null
        do {
            val page = loadPage(workflowId, pageToken)
            jobs.addAll(page.items)
            pageToken = page.nextPageToken?.takeIf { it.isNotBlank() }
            if (pageToken != null && !seenTokens.add(pageToken)) {
                throw IOException("CircleCI repeated a workflow jobs page token")
            }
        } while (pageToken != null)
        jobs.distinctBy { it.id }
    }

    fun approve(workflowId: String, approvalRequestId: String): Completable =
        Completable.fromAction {
            val url = workflowUrl(workflowId).addPathSegment("approve").addPathSegment(approvalRequestId)
            execute<Unit>(request().url(url.build()).post(ByteArray(0).toRequestBody()).build()) { }
        }

    private fun loadPage(workflowId: String, pageToken: String?): JobsPage {
        val url = workflowUrl(workflowId).addPathSegment("job")
        pageToken?.let { url.addQueryParameter("page-token", it) }
        return execute(request().url(url.build()).build()) {
            gson.fromJson(it.body.charStream(), JobsPage::class.java)
        }
    }

    private fun workflowUrl(workflowId: String) =
        "https://circleci.com/api/v2/workflow/".toHttpUrl().newBuilder().addPathSegment(workflowId)

    private fun request() = Request.Builder().header("Circle-Token", token)

    private fun <T> execute(request: Request, read: (Response) -> T): T =
        client.newCall(request).execute().use {
            if (!it.isSuccessful) throw WorkflowHttpException(it.code)
            read(it)
        }

    private data class JobsPage(
        val items: List<WorkflowJob>,
        @SerializedName("next_page_token") val nextPageToken: String?
    )
}

internal class WorkflowHttpException(val status: Int) : IOException("Workflow request failed with HTTP $status")
