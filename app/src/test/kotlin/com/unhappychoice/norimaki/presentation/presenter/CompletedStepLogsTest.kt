package com.unhappychoice.norimaki.presentation.presenter

import com.github.unhappychoice.circleci.v1.response.BuildAction
import com.github.unhappychoice.circleci.v1.response.BuildStep
import com.unhappychoice.norimaki.infrastructure.output.BuildOutputClient
import io.mockk.every
import io.mockk.mockk
import io.reactivex.android.plugins.RxAndroidPlugins
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.schedulers.Schedulers
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
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
    fun displaysSuccessfulDownloadsAroundAnHttpFailure() {
        val step = BuildStep("Run tests", listOf(action("first"), action("failed"), action("last")))
        val presenter = BuildStepPresenter().apply {
            di = DI {
                bind<BuildStep>() with singleton { step }
            }
        }
        val previousIo = RxJavaPlugins.getIoSchedulerHandler()
        val previousMain = RxAndroidPlugins.getOnMainThreadSchedulerHandler()
        RxJavaPlugins.setIoSchedulerHandler { Schedulers.trampoline() }
        RxAndroidPlugins.setMainThreadSchedulerHandler { Schedulers.trampoline() }
        try {
            presenter.getActions(outputClient())

            assertEquals("first output<br>[Some output could not be loaded.]<br>last output", presenter.logString.value)
        } finally {
            RxAndroidPlugins.setMainThreadSchedulerHandler(previousMain)
            RxJavaPlugins.setIoSchedulerHandler(previousIo)
        }
    }

    private fun action(path: String): BuildAction = mockk {
        every { outputUrl } returns "https://example.com/$path"
    }

    private fun outputClient(): BuildOutputClient {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val action = request.url.pathSegments.single()
            val (status, body) = when (action) {
                "failed" -> 503 to "Service Unavailable"
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
        return BuildOutputClient("token", client)
    }
}
