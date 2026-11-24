package com.unhappychoice.norimaki.presentation.view

import android.view.View
import android.widget.TextView
import com.github.unhappychoice.circleci.CircleCIAPIClientV1_1
import com.github.unhappychoice.circleci.v1.response.Build
import com.github.unhappychoice.circleci.v1.response.BuildAction
import com.github.unhappychoice.circleci.v1.response.BuildStep
import com.unhappychoice.norimaki.MainActivity
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.domain.model.BuildId
import com.unhappychoice.norimaki.domain.model.BuildStepId
import com.unhappychoice.norimaki.domain.service.SessionService
import com.unhappychoice.norimaki.infrastructure.output.BuildOutputClient
import com.unhappychoice.norimaki.presentation.presenter.BuildPresenter
import com.unhappychoice.norimaki.presentation.presenter.BuildStepPresenter
import com.unhappychoice.norimaki.presentation.presenter.testSession
import com.unhappychoice.norimaki.presentation.screen.BuildStepScreen
import io.mockk.every
import io.mockk.mockk
import io.reactivex.Observable
import io.reactivex.android.plugins.RxAndroidPlugins
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.schedulers.Schedulers
import io.reactivex.schedulers.TestScheduler
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.singleton
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date
import java.util.concurrent.TimeUnit.SECONDS

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BuildStepRetryTest {
    @Test
    fun retriesTheFinalDownloadOnTheSameScreenFromTheCommittedOffset() {
        val scheduler = TestScheduler()
        val runningBuild = build(finished = false)
        val finishedBuild = build(finished = true)
        var nextBuild = runningBuild
        var finished = false
        val api = mockk<CircleCIAPIClientV1_1> {
            every { getBuild("github", "owner", "repo", 42) } answers {
                finished = nextBuild === finishedBuild
                Observable.just(nextBuild)
            }
        }
        val finalRanges = mutableListOf<String?>()
        var recovered = false
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val range = request.header("Range")
            if (finished) finalRanges += range
            val response = Response.Builder().request(request).protocol(Protocol.HTTP_1_1).message("Test response")
            when {
                !finished -> response.code(200).body("live".toResponseBody())
                range == "bytes=4-" -> response.code(206).header("Content-Range", "bytes 4-8/13")
                    .body(" part".toResponseBody())
                recovered -> response.code(206).header("Content-Range", "bytes 9-12/13").body(" end".toResponseBody())
                else -> response.code(503).body("Service Unavailable".toResponseBody())
            }.build()
        }.build()
        val presenter = BuildStepPresenter { token -> BuildOutputClient(token, http) }
        val screen = BuildStepScreen(BuildId("github", "owner", "repo", 42), BuildStepId(5))

        RxJavaPlugins.setIoSchedulerHandler { scheduler }
        RxJavaPlugins.setComputationSchedulerHandler { scheduler }
        RxAndroidPlugins.setMainThreadSchedulerHandler { Schedulers.trampoline() }
        try {
            Robolectric.buildActivity(MainActivity::class.java).use { controller ->
                val activity = controller.setup().get()
                val container = activity.binding.containerView
                val view = activity.layoutInflater.inflate(screen.getLayoutResource(), container, false) as BuildStepView
                view.di = screen.module(DI {
                    bind<BuildStepPresenter>() with singleton { presenter }
                    bind<MainActivity>() with singleton { activity }
                    bind<SessionService>() with singleton { testSession(api) }
                })
                presenter.di = view.di
                container.addView(view)
                val output = view.findViewById<TextView>(R.id.logText)
                val retry = view.findViewById<View>(R.id.retryStepLoad)
                val error = view.findViewById<View>(R.id.logLoadError)

                scheduler.triggerActions()
                assertEquals("live", output.text.toString())
                assertEquals(View.GONE, retry.visibility)

                nextBuild = finishedBuild
                scheduler.advanceTimeBy(BuildPresenter.BUILD_POLLING_SECONDS + 2, SECONDS)
                assertEquals(listOf("bytes=4-", "bytes=9-", "bytes=9-", "bytes=9-"), finalRanges)
                assertEquals("live part", output.text.toString())
                assertEquals(View.VISIBLE, error.visibility)
                assertEquals(View.VISIBLE, retry.visibility)

                recovered = true
                assertTrue(retry.performClick())
                scheduler.triggerActions()
                assertEquals(listOf("bytes=4-", "bytes=9-", "bytes=9-", "bytes=9-", "bytes=9-"), finalRanges)
                assertEquals("live part end", output.text.toString())
                assertEquals(View.GONE, error.visibility)
                assertEquals(View.GONE, retry.visibility)
            }
        } finally {
            RxAndroidPlugins.reset()
            RxJavaPlugins.reset()
        }
    }

    private fun build(finished: Boolean): Build {
        val action = mockk<BuildAction> {
            every { index } returns 0
            every { step } returns 5
            every { endTime } returns if (finished) Date(0) else null
        }
        return mockk {
            every { steps } returns listOf(BuildStep("Run tests", listOf(action)))
            every { lifecycle } returns if (finished) "finished" else "running"
            every { vcsUrl } returns null
            every { username } returns "owner"
            every { reponame } returns "repo"
            every { buildNum } returns 42
        }
    }
}
