package com.unhappychoice.norimaki.domain.service

import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException

class ArtifactDownloaderTest {
    @Test
    fun streamsBytesAndSendsTokenOnlyBeforeLeavingCircleCi() {
        val requests = mutableListOf<Request>()
        val bytes = byteArrayOf(0, 42, -1)
        val downloader = downloader { request ->
            requests.add(request)
            when (request.url.host) {
                "dl.circleci.com" -> response(request, 302, "https://7-gh.circle-artifacts.com/0/file")
                "7-gh.circle-artifacts.com" -> response(request, 307, "https://storage.example/file?signature=123")
                "storage.example" -> response(request, 302, "https://circleci.com/file")
                else -> response(request, 200, bytes = bytes)
            }
        }
        val output = ByteArrayOutputStream()

        downloader.download("https://dl.circleci.com/private/output/file", TOKEN) { output }
            .test().assertValue(Unit).assertComplete()

        assertArrayEquals(bytes, output.toByteArray())
        assertEquals(listOf(TOKEN, TOKEN, null, null), requests.map { it.header("Circle-Token") })
        assertEquals(listOf(null, null, "signature=123", null), requests.map { it.url.query })
    }

    @Test
    fun rejectsUntrustedArtifactSourcesBeforeMakingARequest() {
        val downloader = downloader { error("Unexpected request") }
        listOf(
            "http://circleci.com/file",
            "https://circleci.com:8443/file",
            "https://circleci.com.example/file",
            "https://notcircle-artifacts.com/file",
            "https://dl.circleci.com@storage.example/file"
        ).forEach { source ->
            downloader.download(source, TOKEN) { error("Unexpected write") }
                .test().assertError(IOException::class.java)
        }
    }

    @Test
    fun rejectsDowngradesAndHttpErrorsBeforeOpeningTheFile() {
        val source = "https://dl.circleci.com/file"
        val downgrade = downloader { response(it, 302, "http://dl.circleci.com/file") }
        val forbidden = downloader { response(it, 403) }

        listOf(downgrade, forbidden).forEach { downloader ->
            downloader.download(source, TOKEN) { error("Unexpected write") }
                .test().assertError(IOException::class.java)
        }
    }

    private fun downloader(respond: (Request) -> Response) =
        ArtifactDownloader(OkHttpClient.Builder().addInterceptor { respond(it.request()) }.build())

    private fun response(request: Request, code: Int, location: String? = null, bytes: ByteArray = byteArrayOf()) =
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("Test response")
            .apply { location?.let { header("Location", it) } }
            .body(bytes.toResponseBody()).build()

    private companion object {
        const val TOKEN = "test-token"
    }
}
