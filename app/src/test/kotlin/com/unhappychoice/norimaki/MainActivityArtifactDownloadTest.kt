package com.unhappychoice.norimaki

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Looper
import android.view.View
import android.widget.ListView
import com.unhappychoice.norimaki.domain.model.BuildId
import com.unhappychoice.norimaki.domain.service.SessionService
import com.unhappychoice.norimaki.presentation.core.ScreenTransition
import com.unhappychoice.norimaki.presentation.screen.ArtifactsScreen
import com.unhappychoice.norimaki.presentation.view.ArtifactsView
import flow.Flow
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.schedulers.Schedulers
import okhttp3.Call
import okhttp3.ResponseBody
import okio.Buffer
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.kodein.di.direct
import org.kodein.di.instance
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(application = ArtifactDownloadTestApplication::class, sdk = [34])
class MainActivityArtifactDownloadTest {
    private val worker = Executors.newSingleThreadExecutor()
    private val build = BuildId("github", "team", "repo", 42)

    @Before
    fun setUp() {
        RxJavaPlugins.setIoSchedulerHandler { Schedulers.from(worker) }
    }

    @After
    fun tearDown() {
        RxJavaPlugins.reset()
        worker.shutdownNow()
    }

    @Test
    fun recreatedArtifactScreenReportsCompletionOfTheOriginalDownload() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val original = controller.get()
            val application = original.application as ArtifactDownloadTestApplication
            val response = HeldArtifactResponse(application.bytes)
            val destination = original.startSaving(application, response)

            controller.recreate()
            val recreated = controller.get()
            assertNotSame(original, recreated)
            assertProgress(recreated, View.VISIBLE)
            assertNull(ShadowToast.getTextOfLatestToast())

            response.finish.countDown()
            drainWorker()
            assertArrayEquals(application.bytes, destination.readBytes())
            assertEquals(1, application.requests.size)
            assertProgress(recreated, View.GONE)
            assertEquals(recreated.getString(R.string.artifact_saved), ShadowToast.getTextOfLatestToast())
        }
    }

    @Test
    fun invalidatingTheSessionAfterRecreationCancelsTheOriginalDownload() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val original = controller.get()
            val application = original.application as ArtifactDownloadTestApplication
            val response = HeldArtifactResponse(application.bytes)
            original.startSaving(application, response)

            controller.recreate()
            application.module.direct.instance<SessionService>().replace("replacement").blockingGet()

            assertTrue(response.call.isCanceled())
            response.finish.countDown()
            drainWorker()
            assertEquals(1, application.requests.size)
            assertProgress(controller.get(), View.GONE)
            assertNull(ShadowToast.getTextOfLatestToast())
        }
    }

    private fun MainActivity.startSaving(
        application: ArtifactDownloadTestApplication,
        response: HeldArtifactResponse
    ): File {
        application.responseBody = response::body
        Flow.get(this).set(ArtifactsScreen(build))
        shadowOf(Looper.getMainLooper()).idleFor(ScreenTransition.DURATION_MILLIS * 2, TimeUnit.MILLISECONDS)
        val destination = File.createTempFile("artifact", ".txt", cacheDir)
        drainWorker()
        val list = findViewById<ListView>(R.id.artifact_list)
        assertTrue(list.performItemClick(View(this), 0, 0L))
        val picker = shadowOf(this).nextStartedActivityForResult
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, picker.intent.action)
        val result = Intent().setData(Uri.fromFile(destination))
        assertTrue(activityResultRegistry.dispatchResult(picker.requestCode, Activity.RESULT_OK, result))
        assertTrue(response.started.await(5, TimeUnit.SECONDS))
        assertArrayEquals(application.bytes.copyOf(1), destination.readBytes())
        return destination
    }

    private fun assertProgress(activity: MainActivity, visibility: Int) {
        val view = activity.binding.containerView.getChildAt(0) as ArtifactsView
        assertEquals(visibility, view.findViewById<View>(R.id.artifact_downloading).visibility)
    }

    private fun drainWorker() {
        worker.submit {}.get(5, TimeUnit.SECONDS)
        shadowOf(Looper.getMainLooper()).idle()
    }
}

private class HeldArtifactResponse(private val bytes: ByteArray) {
    val started = CountDownLatch(1)
    val finish = CountDownLatch(1)
    lateinit var call: Call
        private set

    fun body(download: Call): ResponseBody {
        call = download
        return object : ResponseBody() {
            private val body = object : Source {
                private var offset = 0

                override fun read(sink: Buffer, byteCount: Long): Long {
                    if (offset == bytes.size) return -1
                    val count = if (offset == 0) 1 else {
                        started.countDown()
                        if (!finish.await(15, TimeUnit.SECONDS) || download.isCanceled()) {
                            throw IOException("Response interrupted")
                        }
                        bytes.size - offset
                    }
                    sink.write(bytes, offset, count)
                    offset += count
                    return count.toLong()
                }

                override fun timeout() = Timeout.NONE
                override fun close() = Unit
            }.buffer()

            override fun contentType() = null
            override fun contentLength() = bytes.size.toLong()
            override fun source() = body
        }
    }
}
