package com.unhappychoice.norimaki.presentation.view

import android.os.Looper
import android.view.View
import android.widget.Button
import androidx.recyclerview.widget.RecyclerView
import com.github.unhappychoice.circleci.v1.response.Project
import com.unhappychoice.norimaki.BackTestApplication
import com.unhappychoice.norimaki.MainActivity
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.presentation.adapter.ProjectAdapter
import io.mockk.mockk
import io.reactivex.Observable
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.schedulers.Schedulers
import java.io.IOException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = BackTestApplication::class, sdk = [34])
class NavigationViewTest {
    private val application get() = RuntimeEnvironment.getApplication() as BackTestApplication

    @Before
    fun useSynchronousRequests() {
        RxJavaPlugins.setIoSchedulerHandler { Schedulers.trampoline() }
    }

    @After
    fun restoreSchedulers() {
        RxJavaPlugins.reset()
    }

    @Test
    fun retriesFailedProjectRequestWithTheVerifiedSession() {
        var requests = 0
        val projects = listOf(mockk<Project>(relaxed = true))
        application.projects = Observable.defer {
            if (++requests == 1) Observable.error(IOException("HTTP 503"))
            else Observable.just(projects)
        }

        withPicker { picker ->
            val retry = picker.findViewById<Button>(R.id.retryProjects)
            assertEquals(1, requests)
            assertEquals(View.VISIBLE, picker.errorVisibility)
            assertEquals(0, picker.projectAdapter.itemCount)

            assertTrue(retry.performClick())
            drainUi()

            assertEquals(2, requests)
            assertEquals(View.GONE, picker.errorVisibility)
            assertEquals(projects, picker.projectAdapter.projects.value)
            assertEquals(listOf("saved"), application.verifiedTokens)
        }
    }

    @Test
    fun successfulEmptyResultShowsAnEmptyPickerWithoutAnError() {
        application.projects = Observable.just(emptyList())

        withPicker { picker ->
            assertEquals(0, picker.projectAdapter.itemCount)
            assertEquals(View.GONE, picker.errorVisibility)
            assertEquals(listOf("saved"), application.verifiedTokens)
        }
    }

    private fun withPicker(test: (NavigationView) -> Unit) {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            val picker = activity.binding.navigationView
            activity.binding.drawerLayout.openDrawer(picker, false)
            drainUi()
            test(picker)
        }
    }

    private val NavigationView.errorVisibility get() = findViewById<View>(R.id.projectLoadError).visibility
    private val NavigationView.projectAdapter
        get() = findViewById<RecyclerView>(R.id.projectsView).adapter as ProjectAdapter

    private fun drainUi() = shadowOf(Looper.getMainLooper()).idle()
}
