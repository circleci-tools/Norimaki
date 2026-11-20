package com.unhappychoice.norimaki

import com.github.unhappychoice.circleci.CircleCIAPIClientV1_1
import com.github.unhappychoice.circleci.v1.response.Artifact
import com.unhappychoice.norimaki.domain.service.ArtifactDownloader
import com.unhappychoice.norimaki.domain.service.SessionService
import com.unhappychoice.norimaki.presentation.presenter.testSession
import io.mockk.every
import io.mockk.mockk
import io.reactivex.Observable
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.singleton

class ArtifactDownloadTestApplication : NorimakiApplication() {
    val bytes = byteArrayOf(0, 42, -1)
    val requests = mutableListOf<Request>()

    override val module by lazy {
        DI {
            extend(super.module, allowOverride = true)
            bind<SessionService>(overrides = true) with singleton { testSession(api) }
            bind<ArtifactDownloader>(overrides = true) with singleton { ArtifactDownloader(http) }
        }
    }

    private val api = mockk<CircleCIAPIClientV1_1> {
        every { getProjects() } returns Observable.never()
        every { getRecentBuilds(any(), any()) } returns Observable.never()
        every { getBuild("github", "team", "repo", 42) } returns Observable.never()
        every { getArtifacts("github", "team", "repo", 43) } returns Observable.just(emptyList())
        every { getArtifacts("github", "team", "repo", 42) } returns Observable.just(
            listOf(
                Artifact(0, "reports/check.txt", "check.txt", "https://dl.circleci.com/file"),
                Artifact(0, "reports/", "reports/", "https://dl.circleci.com/file")
            )
        )
    }

    private val http = OkHttpClient.Builder().addInterceptor { chain ->
        val request = chain.request()
        requests.add(request)
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .body(bytes.toResponseBody()).build()
    }.build()
}
