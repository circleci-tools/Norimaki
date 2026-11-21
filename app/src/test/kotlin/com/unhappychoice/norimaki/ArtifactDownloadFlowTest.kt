package com.unhappychoice.norimaki

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.ListView
import android.widget.PopupMenu
import android.widget.TextView
import com.github.unhappychoice.circleci.v1.response.Artifact
import com.unhappychoice.norimaki.domain.model.BuildId
import com.unhappychoice.norimaki.domain.service.SessionService
import com.unhappychoice.norimaki.presentation.core.ScreenTransition
import com.unhappychoice.norimaki.presentation.screen.ArtifactsScreen
import com.unhappychoice.norimaki.presentation.screen.BuildScreen
import com.unhappychoice.norimaki.presentation.view.ArtifactsView
import flow.Flow
import io.mockk.mockk
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.schedulers.TestScheduler
import java.io.File
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

@RunWith(RobolectricTestRunner::class)
@Config(application = ArtifactDownloadTestApplication::class, sdk = [34])
class ArtifactDownloadFlowTest {
    private val io = TestScheduler()
    private val buildId = BuildId("github", "team", "repo", 42)

    @Before
    fun controlIo() {
        RxJavaPlugins.setIoSchedulerHandler { io }
    }

    @After
    fun restoreIo() {
        RxJavaPlugins.reset()
    }

    @Test
    fun savesTheSelectedJobArtifactAfterPickerCancellationAndRetry() = withArtifacts { activity, app ->
        val list = activity.findViewById<ListView>(R.id.artifact_list)
        val progress = activity.findViewById<View>(R.id.artifact_downloading)
        val picker = activity.selectArtifact(list)
        list.performItemClick(View(activity), 0, 0L)
        assertNull(shadowOf(activity).nextStartedActivityForResult)
        assertTrue(activity.activityResultRegistry.dispatchResult(picker, Activity.RESULT_CANCELED, null))
        assertTrue(app.requests.isEmpty())

        val destination = File.createTempFile("artifact-", ".txt", activity.cacheDir)
        activity.deliverDestination(activity.selectArtifact(list), destination)
        assertFalse(list.isEnabled)
        assertEquals(View.VISIBLE, progress.visibility)
        list.performItemClick(View(activity), 0, 0L)
        assertNull(shadowOf(activity).nextStartedActivityForResult)
        flushIo()

        assertArrayEquals(app.bytes, destination.readBytes())
        assertEquals("test-token", app.requests.single().header("Circle-Token"))
        assertTrue(list.isEnabled)
        assertEquals(View.GONE, progress.visibility)
        assertEquals(activity.getString(R.string.artifact_saved), ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun savesTheSelectedArtifactAfterProcessRecreationAndSessionRestoration() {
        withRestoredPicker("test-token") { app, destination ->
            assertArrayEquals(app.bytes, destination.readBytes())
            assertEquals("https://dl.circleci.com/file", app.requests.single().url.toString())
            assertEquals("test-token", app.requests.single().header("Circle-Token"))
        }
    }

    @Test
    fun discardsARestoredPickerResultWhenADifferentTokenIsVerified() {
        withRestoredPicker("replacement-token") { app, destination ->
            assertTrue(app.requests.isEmpty())
            assertEquals(0L, destination.length())
        }
    }

    @Test
    fun defersSavingThePickerResultUntilTheActivityResumes() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            val app = activity.application as ArtifactDownloadTestApplication
            activity.openArtifacts()
            val picker = activity.selectArtifact(activity.findViewById(R.id.artifact_list))
            val destination = File.createTempFile("artifact-", ".txt", activity.cacheDir)
            controller.pause()
            activity.deliverDestination(picker, destination)
            flushIo()
            assertTrue(app.requests.isEmpty())
            assertEquals(0L, destination.length())

            controller.resume().postResume()
            flushIo()

            assertArrayEquals(app.bytes, destination.readBytes())
            assertEquals(1, app.requests.size)
        }
    }

    @Test
    fun reportsAnUnwritableDestinationAndAllowsAnotherAttempt() = withArtifacts { activity, app ->
        val list = activity.findViewById<ListView>(R.id.artifact_list)
        activity.deliverDestination(activity.selectArtifact(list), activity.cacheDir)
        flushIo()

        assertEquals(1, app.requests.size)
        assertEquals(activity.getString(R.string.artifact_save_failed), ShadowToast.getTextOfLatestToast())
        assertTrue(list.isEnabled)
        activity.selectArtifact(list, position = 1, path = "reports/", filename = "artifact")
    }

    @Test
    fun treatsASuccessfulPickerResultWithoutAUriAsCancellation() = withArtifacts { activity, app ->
        val list = activity.findViewById<ListView>(R.id.artifact_list)
        val picker = activity.selectArtifact(list)
        assertTrue(activity.activityResultRegistry.dispatchResult(picker, Activity.RESULT_OK, Intent()))
        flushIo()

        assertTrue(app.requests.isEmpty())
        assertTrue(list.isEnabled)
        activity.selectArtifact(list)
    }

    @Test
    fun ignoresThePickerResultAfterReturningToTheBuild() = withArtifacts { activity, app ->
        val picker = activity.selectArtifact(activity.findViewById(R.id.artifact_list))
        activity.onBackPressedDispatcher.onBackPressed()
        finishTransition()
        assertEquals(BuildScreen(buildId), Flow.get(activity).history.top<Any>())

        val destination = File.createTempFile("artifact-", ".txt", activity.cacheDir)
        activity.deliverDestination(picker, destination)
        flushIo()

        assertTrue(app.requests.isEmpty())
        assertEquals(0L, destination.length())
    }

    @Test
    fun letsTheUserRetryWhenNoDocumentPickerIsAvailable() = withArtifacts { activity, app ->
        val list = activity.findViewById<ListView>(R.id.artifact_list)
        val application = shadowOf(app)
        application.checkActivities(true)
        try {
            list.performItemClick(View(activity), 0, 0L)
            assertEquals(activity.getString(R.string.artifact_save_failed), ShadowToast.getTextOfLatestToast())
        } finally {
            application.checkActivities(false)
        }
        activity.selectArtifact(list)
    }

    @Test
    fun ignoresThePickerResultAfterTheSessionChanges() = withArtifacts { activity, app ->
        val list = activity.findViewById<ListView>(R.id.artifact_list)
        val picker = activity.selectArtifact(list)
        val artifact = Artifact(0, "reports/check.txt", "check.txt", "https://dl.circleci.com/file")
        val presenter = (list.parent as ArtifactsView).presenter
        val session = app.module.direct.instance<SessionService>()
        session.replace("replacement-token").test().assertComplete()
        val destination = File.createTempFile("artifact-", ".txt", activity.cacheDir)
        activity.deliverDestination(picker, destination)
        presenter.chooseDestination(artifact)
        assertNull(shadowOf(activity).nextStartedActivityForResult)
        flushIo()

        assertTrue(app.requests.isEmpty())
        assertEquals(0L, destination.length())
    }

    @Test
    fun cancelsAQueuedDownloadWhenTheSessionChanges() = withArtifacts { activity, app ->
        val list = activity.findViewById<ListView>(R.id.artifact_list)
        val destination = File.createTempFile("artifact-", ".txt", activity.cacheDir)
        activity.deliverDestination(activity.selectArtifact(list), destination)
        assertFalse(list.isEnabled)

        app.module.direct.instance<SessionService>().replace("replacement-token").test().assertComplete()
        flushIo()

        assertTrue(app.requests.isEmpty())
        assertEquals(0L, destination.length())
        assertTrue(list.isEnabled)
        assertEquals(View.GONE, list.visibility)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.artifact_downloading).visibility)
        val status = activity.findViewById<TextView>(R.id.artifact_status)
        val retry = activity.findViewById<Button>(R.id.retry_artifacts)
        assertEquals(View.VISIBLE, status.visibility)
        assertEquals(activity.getString(R.string.artifacts_load_failed), status.text.toString())
        assertEquals(View.VISIBLE, retry.visibility)
        assertTrue(retry.performClick())
        flushIo()
        assertEquals(View.VISIBLE, status.visibility)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.artifact_loading).visibility)
    }

    @Test
    fun ignoresThePickerResultWhenViewingAnotherJob() = withArtifacts { activity, app ->
        val picker = activity.selectArtifact(activity.findViewById(R.id.artifact_list))
        Flow.get(activity).set(ArtifactsScreen(buildId.copy(number = 43)))
        finishTransition()
        val destination = File.createTempFile("artifact-", ".txt", activity.cacheDir)
        activity.deliverDestination(picker, destination)
        flushIo()

        assertTrue(app.requests.isEmpty())
        assertEquals(0L, destination.length())
    }

    private fun withRestoredPicker(
        token: String,
        assertResult: (ArtifactDownloadTestApplication, File) -> Unit
    ) {
        val savedState = Bundle()
        val original = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = original.get()
        val app = activity.application as ArtifactDownloadTestApplication
        activity.openArtifacts()
        val picker = activity.selectArtifact(activity.findViewById(R.id.artifact_list))
        val destination = File.createTempFile("artifact-", ".txt", activity.cacheDir)
        original.pause().stop().saveInstanceState(savedState).destroy()
        val verification = app.resetSession(token)

        Robolectric.buildActivity(MainActivity::class.java).setup(savedState).use { recreated ->
            val restored = recreated.get()
            assertEquals(ArtifactsScreen(buildId), Flow.get(restored).history.top<Any>())
            restored.deliverDestination(picker, destination)
            flushIo()
            assertTrue(app.requests.isEmpty())

            verification.onSuccess(mockk())
            flushIo()

            assertResult(app, destination)
        }
    }

    private fun withArtifacts(test: (MainActivity, ArtifactDownloadTestApplication) -> Unit) {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            val app = activity.application as ArtifactDownloadTestApplication
            activity.openArtifacts()
            test(activity, app)
        }
    }

    private fun MainActivity.openArtifacts() {
        Flow.get(this).set(BuildScreen(buildId))
        finishTransition()
        val menu = PopupMenu(this, binding.toolbar).menu
        onCreateOptionsMenu(menu)
        onOptionsItemSelected(menu.findItemByTitle(getString(R.string.artifacts)))
        finishTransition()

        assertEquals(ArtifactsScreen(buildId), Flow.get(this).history.top<Any>())
        assertEquals("Artifacts · repo #42", supportActionBar?.title)
    }

    private fun MainActivity.selectArtifact(
        list: ListView,
        position: Int = 0,
        path: String = "reports/check.txt",
        filename: String = "check.txt"
    ): Int {
        assertEquals(path, list.adapter.getItem(position))
        assertTrue(list.performItemClick(View(this), position, 0L))
        val picker = shadowOf(this).nextStartedActivityForResult
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, picker.intent.action)
        assertEquals(filename, picker.intent.getStringExtra(Intent.EXTRA_TITLE))
        return picker.requestCode
    }

    private fun MainActivity.deliverDestination(request: Int, file: File) {
        val result = Intent().setData(Uri.fromFile(file))
        assertTrue(activityResultRegistry.dispatchResult(request, Activity.RESULT_OK, result))
    }

    private fun finishTransition() {
        shadowOf(Looper.getMainLooper()).idleFor(ScreenTransition.DURATION_MILLIS * 2, TimeUnit.MILLISECONDS)
        flushIo()
    }

    private fun flushIo() {
        io.triggerActions()
        shadowOf(Looper.getMainLooper()).idle()
    }
}

private fun android.view.Menu.findItemByTitle(title: String) =
    (0 until size()).map(::getItem).single { it.title == title }
