package com.unhappychoice.norimaki.infrastructure.workflow

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.unhappychoice.norimaki.domain.model.WorkflowJob
import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.exceptions.Exceptions
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference

class WorkflowClient(
    private val token: String,
    private val client: OkHttpClient = OkHttpClient.Builder().followRedirects(false).retryOnConnectionFailure(false).build(),
    private val gson: Gson = Gson()
) {
    fun jobs(workflowId: String): Single<List<WorkflowJob>> = Single.create { emitter ->
        val activeCall = AtomicReference<Call>()
        emitter.setCancellable { activeCall.getAndSet(null)?.cancel() }
        try {
            val jobs = loadJobs { pageToken ->
                if (emitter.isDisposed) return@create
                val call = client.newCall(jobsRequest(workflowId, pageToken))
                activeCall.set(call)
                try {
                    if (emitter.isDisposed) return@create
                    execute(call) { gson.fromJson(it.body.charStream(), JobsPage::class.java) }
                } finally {
                    activeCall.compareAndSet(call, null)
                }
            }
            emitter.onSuccess(jobs)
        } catch (error: Throwable) {
            Exceptions.throwIfFatal(error)
            emitter.tryOnError(error)
        }
    }

    fun approve(workflowId: String, approvalRequestId: String): Completable =
        Completable.fromAction {
            val url = workflowUrl(workflowId).addPathSegment("approve").addPathSegment(approvalRequestId)
            val request = request().url(url.build()).post(ByteArray(0).toRequestBody()).build()
            execute<Unit>(client.newCall(request)) { }
        }

    private inline fun loadJobs(loadPage: (String?) -> JobsPage): List<WorkflowJob> {
        val jobs = mutableListOf<WorkflowJob>()
        val seenTokens = mutableSetOf<String>()
        var pageToken: String? = null
        do {
            val page = loadPage(pageToken)
            jobs.addAll(page.items)
            pageToken = page.nextPageToken?.takeIf { it.isNotBlank() }
            if (pageToken != null && !seenTokens.add(pageToken)) {
                throw IOException("CircleCI repeated a workflow jobs page token")
            }
        } while (pageToken != null)
        return jobs.distinctBy { it.id }
    }

    private fun jobsRequest(workflowId: String, pageToken: String?): Request {
        val url = workflowUrl(workflowId).addPathSegment("job")
        pageToken?.let { url.addQueryParameter("page-token", it) }
        return request().url(url.build()).build()
    }

    private fun workflowUrl(workflowId: String) =
        "https://circleci.com/api/v2/workflow/".toHttpUrl().newBuilder().addPathSegment(workflowId)

    private fun request() = Request.Builder().header("Circle-Token", token)

    private fun <T> execute(call: Call, read: (Response) -> T): T =
        call.execute().use {
            if (!it.isSuccessful) throw WorkflowHttpException(it.code)
            read(it)
        }

    private data class JobsPage(
        val items: List<WorkflowJob>,
        @SerializedName("next_page_token") val nextPageToken: String?
    )
}

internal class WorkflowHttpException(val status: Int) : IOException("Workflow request failed with HTTP $status")
