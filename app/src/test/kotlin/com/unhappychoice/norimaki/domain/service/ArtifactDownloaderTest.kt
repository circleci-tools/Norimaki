package com.unhappychoice.norimaki.domain.service

import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import io.reactivex.observers.TestObserver
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream

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
            "not a URL",
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
    fun reportsCancellationBeforeOpeningTheDestination() {
        val observer = TestObserver<Unit>()
        var cleanedUp = false
        val downloader = downloader {
            observer.dispose()
            response(it, 200, bytes = byteArrayOf(1))
        }

        downloader.download("https://dl.circleci.com/file", TOKEN, { cleanedUp = true }) {
            error("Unexpected write")
        }.subscribe(observer)

        observer.assertNoValues().assertNoErrors().assertNotComplete()
        assertEquals(true, cleanedUp)
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

    @Test
    fun cleansUpWhenWritingFails() {
        val downloader = downloader { response(it, 200, bytes = byteArrayOf(1)) }
        val failure = IOException("Destination is full")
        val events = mutableListOf<String>()

        downloader.download("https://dl.circleci.com/file", TOKEN, { events.add("cleanup") }) {
            object : OutputStream() {
                override fun write(value: Int) = throw failure
                override fun close() {
                    events.add("close")
                }
            }
        }.test().assertError(failure).assertNoValues()

        assertEquals(listOf("close", "cleanup"), events)
    }

    @Test
    fun stopsFollowingRedirectsBeforeOpeningTheDestination() {
        var requests = 0
        val downloader = downloader {
            requests++
            response(it, 302, "/file")
        }

        downloader.download("https://dl.circleci.com/file", TOKEN) { error("Unexpected write") }
            .test().assertError { it is IOException && it.message == "Too many artifact redirects" }

        assertEquals(11, requests)
    }

    @Test
    fun rejectsARedirectWithoutALocationAndCleansUpTheDestination() {
        val downloader = downloader { response(it, 302) }
        var cleanedUp = false

        downloader.download("https://dl.circleci.com/file", TOKEN, { cleanedUp = true }) {
            error("Unexpected write")
        }.test().assertError { it is IOException && it.message == "Invalid artifact redirect" }

        assertEquals(true, cleanedUp)
    }

    @Test
    fun cleansUpIfTheDownloadIsDisposedBeforeReportingSuccess() {
        val downloader = downloader { response(it, 200, bytes = byteArrayOf(1)) }
        val observer = TestObserver<Unit>()
        var cleanedUp = false

        downloader.download("https://dl.circleci.com/file", TOKEN, { cleanedUp = true }) {
            object : ByteArrayOutputStream() {
                override fun close() = observer.dispose()
            }
        }.subscribe(observer)

        observer.assertNoValues().assertNotComplete()
        assertEquals(true, cleanedUp)
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
