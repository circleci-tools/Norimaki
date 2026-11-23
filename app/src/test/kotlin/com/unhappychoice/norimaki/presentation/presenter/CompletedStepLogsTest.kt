package com.unhappychoice.norimaki.presentation.presenter

import com.github.unhappychoice.circleci.CircleCIAPIClientV1_1
import com.github.unhappychoice.circleci.v1.response.Build
import com.github.unhappychoice.circleci.v1.response.BuildAction
import com.github.unhappychoice.circleci.v1.response.BuildStep
import com.unhappychoice.norimaki.MainActivity
import com.unhappychoice.norimaki.domain.model.BuildId
import com.unhappychoice.norimaki.domain.model.BuildStepId
import com.unhappychoice.norimaki.domain.service.SessionService
import com.unhappychoice.norimaki.infrastructure.output.BuildOutputClient
import com.unhappychoice.norimaki.presentation.view.BuildStepView
import io.mockk.every
import io.mockk.mockk
import io.reactivex.Observable
import io.reactivex.android.plugins.RxAndroidPlugins
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.schedulers.Schedulers
import mortar.MortarScope
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.singleton
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CompletedStepLogsTest {
    @Test
    fun displaysSuccessfulDownloadsWithFailureAndLimitNotices() {
        val step = BuildStep("Run tests", listOf(action("first"), action("failed"), action("capped"), action("last")))
        val build = mockk<Build> { every { steps } returns listOf(step) }
        val api = mockk<CircleCIAPIClientV1_1> {
            every { getBuild("github", "owner", "repo", 42) } returns Observable.just(build)
        }
        val presenter = BuildStepPresenter(::outputClient).apply {
            di = DI {
                bind<BuildId>() with singleton { BuildId("github", "owner", "repo", 42) }
                bind<BuildStepId>() with singleton { BuildStepId(null, legacyName = step.name) }
                bind<MainActivity>() with singleton {
                    mockk<MainActivity>().also { PollingLifecycle(it).start() }
                }
                bind<SessionService>() with singleton { testSession(api) }
            }
        }
        val previousIo = RxJavaPlugins.getIoSchedulerHandler()
        val previousComputation = RxJavaPlugins.getComputationSchedulerHandler()
        val previousMain = RxAndroidPlugins.getOnMainThreadSchedulerHandler()
        RxJavaPlugins.setIoSchedulerHandler { Schedulers.trampoline() }
        RxJavaPlugins.setComputationSchedulerHandler { Schedulers.trampoline() }
        RxAndroidPlugins.setMainThreadSchedulerHandler { Schedulers.trampoline() }
        var scope: MortarScope? = null
        try {
            scope = presenter.attachToTestScope(mockk<BuildStepView>(relaxUnitFun = true))

            val log = presenter.log
            assertEquals(
                listOf(
                    "first output",
                    "[Some output could not be loaded.]",
                    "accepted",
                    "[Action output limit reached.]",
                    "last output"
                ),
                (0 until log.lineCount).map { line -> log[line].joinToString("") { it.text } }
            )
            assertEquals(false, presenter.logLoadFailed.value)
        } finally {
            scope?.destroy()
            RxAndroidPlugins.setMainThreadSchedulerHandler(previousMain)
            RxJavaPlugins.setComputationSchedulerHandler(previousComputation)
            RxJavaPlugins.setIoSchedulerHandler(previousIo)
        }
    }

    @Test
    fun returnsSuccessfulDownloadsAroundAnHttpFailure() {
        val urls = listOf("first", "failed", "last").map { "https://example.com/$it" }

        completedActionLogs(urls, fetchOutput = outputClient("token")::fetchOutput)
            .test()
            .assertResult("first output", "\n[Some output could not be loaded.]\n", "last output")
    }

    private fun action(path: String): BuildAction = mockk {
        every { step } returns null
        every { endTime } returns Date(0)
        every { outputUrl } returns "https://example.com/$path"
    }

    private fun outputClient(token: String): BuildOutputClient {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val action = request.url.pathSegments.single()
            val (status, body) = when (action) {
                "failed" -> 503 to "Service Unavailable"
                "capped" -> 200 to """[{"message":"accepted"},{"message":"${"x".repeat(100)}"}]"""
                else -> 200 to """[{"message":"$action "},{"message":"output"}]"""
            }
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(status)
                .message("Test response")
                .body(body.toResponseBody())
                .build()
        }.build()
        return BuildOutputClient(token, client, outputByteLimit = 64)
    }
}
